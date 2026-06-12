package com.yu.aiagent.chatmemory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yu.aiagent.model.entity.ChatMemory;
import com.yu.aiagent.service.ChatMemoryService;
import com.yu.aiagent.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 MySQL 的 Spring AI 对话记忆仓库。
 *
 * <p>Spring AI 的 {@link ChatMemoryRepository} 是一个“记忆仓库接口”，
 * {@code MessageWindowChatMemory} 会通过这个接口读取和保存指定 conversationId 的历史消息。</p>
 *
 * <p>当前项目里，AI 股票大师使用 Spring AI 的聊天记忆机制，所以需要实现这个仓库，
 * 把原本存在 JVM 内存里的聊天窗口持久化到 MySQL 的 {@code chat_memory} 表。</p>
 *
 * <p>这个类只服务 AI 股票大师，因此所有读写都会带上 {@code appType = stock_app}，
 * 避免误读或误删 AI 超级智能体 {@code appType = manus} 的会话记录。</p>
 */
@Repository
@Slf4j
@RequiredArgsConstructor
public class MysqlChatMemoryRepository implements ChatMemoryRepository {

    /**
     * 当前仓库对应的应用类型。
     */
    private static final String DEFAULT_APP_TYPE = "stock_app";

    /**
     * 操作 chat_memory 表的业务 Service。
     */
    private final ChatMemoryService chatMemoryService;

    /**
     * 用于从当前 Session 中获取登录用户。
     */
    private final UserService userService;

    /**
     * conversationId 和 userId 的绑定关系。
     *
     * <p>SSE 流式响应会进入异步线程，异步线程里不一定还能拿到当前 request。
     * 因此 Controller 在发起聊天前会调用 {@link #bindConversationUser(String, Long)}，
     * 先把本次会话属于哪个用户记录下来，保存消息时再从这里兜底获取 userId。</p>
     */
    private final Map<String, Long> conversationUserIdMap = new ConcurrentHashMap<>();

    /**
     * 绑定会话和当前登录用户。
     *
     * @param conversationId 前端传入的会话 id，也就是 chat_memory.conversationId
     * @param userId         当前登录用户 id
     */
    public void bindConversationUser(String conversationId, Long userId) {
        if (conversationId != null && userId != null) {
            conversationUserIdMap.put(conversationId, userId);
        }
    }

    /**
     * 查询所有可用的会话 id。
     *
     * <p>这是 Spring AI 接口要求实现的方法。当前业务主要通过自己的会话列表接口查询历史会话，
     * 这个方法更多是给 Spring AI 内部机制使用。</p>
     *
     * @return 未删除的股票大师会话 id 列表
     */
    @Override
    public List<String> findConversationIds() {
        QueryWrapper<ChatMemory> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("conversationId")
                .eq("appType", DEFAULT_APP_TYPE)
                .eq("isDelete", 0)
                .groupBy("conversationId");
        return chatMemoryService.list(queryWrapper).stream()
                .map(ChatMemory::getConversationId)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 根据会话 id 查询历史消息。
     *
     * <p>AI 股票大师每次收到新问题时，Spring AI 会先调用这里，
     * 把 MySQL 中保存的历史消息恢复成 {@link Message} 列表，再交给大模型作为上下文。</p>
     *
     * @param conversationId 会话 id
     * @return Spring AI 可识别的历史消息列表
     */
    @Override
    public List<Message> findByConversationId(String conversationId) {
        LambdaQueryWrapper<ChatMemory> queryWrapper = Wrappers.lambdaQuery(ChatMemory.class)
                .select(ChatMemory::getId,
                        ChatMemory::getConversationId,
                        ChatMemory::getMessageType,
                        ChatMemory::getContent,
                        ChatMemory::getMessageOrder,
                        ChatMemory::getIsDelete)
                .eq(ChatMemory::getConversationId, conversationId)
                .eq(ChatMemory::getAppType, DEFAULT_APP_TYPE)
                .eq(ChatMemory::getIsDelete, 0)
                .orderByAsc(ChatMemory::getMessageOrder)
                .orderByAsc(ChatMemory::getId);

        // 如果能确定当前用户，就加上 userId 条件，防止不同用户读到同名 conversationId。
        Long currentUserId = getCurrentUserId(conversationId);
        if (currentUserId != null) {
            queryWrapper.eq(ChatMemory::getUserId, currentUserId);
        }

        return chatMemoryService.list(queryWrapper).stream()
                .map(this::toMessage)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 保存当前会话窗口的完整消息列表。
     *
     * <p>注意：Spring AI 传进来的 {@code messages} 不是“本轮新增消息”，
     * 而是当前记忆窗口里的完整消息列表。因此这里不能直接追加保存，否则每轮对话都会重复写入旧消息。</p>
     *
     * <p>正确做法是：先把该会话旧窗口软删除，再把新窗口批量写入。
     * 这样数据库中始终保留当前窗口的一份有效记录，历史软删除数据也还在。</p>
     *
     * @param conversationId 会话 id
     * @param messages       Spring AI 当前记忆窗口内的完整消息列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAll(String conversationId, List<Message> messages) {
        deleteByConversationId(conversationId);
        if (messages == null || messages.isEmpty()) {
            return;
        }

        List<ChatMemory> rows = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            ChatMemory row = toEntity(conversationId, messages.get(i), i);
            if (row != null) {
                rows.add(row);
            }
        }
        if (!rows.isEmpty()) {
            chatMemoryService.saveBatch(rows);
        }
    }

    /**
     * 软删除指定会话的当前有效窗口。
     *
     * <p>这里是逻辑删除，不是物理删除。也就是把 {@code isDelete} 更新为 1，
     * 后续普通查询只看 {@code isDelete = 0} 的新窗口记录。</p>
     *
     * @param conversationId 会话 id
     */
    @Override
    public void deleteByConversationId(String conversationId) {
        Long currentUserId = getCurrentUserId(conversationId);
        LambdaUpdateWrapper<ChatMemory> updateWrapper = Wrappers.lambdaUpdate(ChatMemory.class)
                // 逻辑删除字段需要显式 set，否则 MyBatis-Plus 可能生成没有 SET 的 UPDATE。
                .set(ChatMemory::getIsDelete, 1)
                .eq(ChatMemory::getConversationId, conversationId)
                .eq(ChatMemory::getAppType, DEFAULT_APP_TYPE)
                .eq(ChatMemory::getIsDelete, 0);

        if (currentUserId != null) {
            updateWrapper.eq(ChatMemory::getUserId, currentUserId);
        }
        chatMemoryService.update(updateWrapper);
    }

    /**
     * 把 Spring AI 的 Message 转换成 chat_memory 表实体。
     *
     * <p>TOOL 类型消息暂不保存，因为工具响应结构较复杂，当前股票大师只需要恢复用户消息和 AI 回复。</p>
     */
    private ChatMemory toEntity(String conversationId, Message message, int messageOrder) {
        if (message == null || message.getMessageType() == MessageType.TOOL) {
            return null;
        }

        ChatMemory chatMemory = new ChatMemory();
        chatMemory.setConversationId(conversationId);
        chatMemory.setUserId(getCurrentUserId(conversationId));
        chatMemory.setAppType(DEFAULT_APP_TYPE);
        chatMemory.setMessageType(message.getMessageType().name());
        chatMemory.setContent(message.getText());
        chatMemory.setMessageOrder(messageOrder);
        chatMemory.setIsDelete(0);
        return chatMemory;
    }

    /**
     * 把数据库中的消息记录还原成 Spring AI 的 Message 对象。
     */
    private Message toMessage(ChatMemory chatMemory) {
        try {
            MessageType messageType = MessageType.valueOf(chatMemory.getMessageType());
            return switch (messageType) {
                case USER -> new UserMessage(chatMemory.getContent());
                case ASSISTANT -> new AssistantMessage(chatMemory.getContent());
                case SYSTEM -> new SystemMessage(chatMemory.getContent());
                case TOOL -> null;
            };
        } catch (IllegalArgumentException e) {
            log.warn("跳过无法识别的历史消息类型: {}", chatMemory.getMessageType());
            return null;
        }
    }

    /**
     * 获取当前会话对应的用户 id。
     *
     * <p>优先从 conversationUserIdMap 中取，因为 SSE 异步线程里 request 上下文可能已经不可用。
     * 如果没有提前绑定，再尝试从当前 request 的 Session 中获取登录用户。</p>
     */
    private Long getCurrentUserId(String conversationId) {
        Long boundUserId = conversationUserIdMap.get(conversationId);
        if (boundUserId != null) {
            return boundUserId;
        }

        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            return userService.getLoginUser(request).getId();
        } catch (Exception e) {
            return null;
        }
    }
}
