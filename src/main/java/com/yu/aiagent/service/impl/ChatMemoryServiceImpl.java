package com.yu.aiagent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yu.aiagent.exception.BusinessException;
import com.yu.aiagent.exception.ErrorCode;
import com.yu.aiagent.mapper.ChatMemoryMapper;
import com.yu.aiagent.model.entity.ChatMemory;
import com.yu.aiagent.model.vo.ManusConversationVO;
import com.yu.aiagent.model.vo.ManusMessageVO;
import com.yu.aiagent.model.vo.StockConversationVO;
import com.yu.aiagent.model.vo.StockMessageVO;
import com.yu.aiagent.service.ChatMemoryService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 对话记忆表 chat_memory 的业务实现。
 *
 * <p>这层负责把数据库里的消息记录整理成前端需要的会话列表、消息列表，
 * 也负责把 AI 超级智能体运行时的 Spring AI Message 持久化为数据库记录。</p>
 */
@Service
public class ChatMemoryServiceImpl extends ServiceImpl<ChatMemoryMapper, ChatMemory>
        implements ChatMemoryService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * AI 股票大师在 chat_memory 表中的应用类型。
     */
    private static final String STOCK_APP_TYPE = "stock_app";

    /**
     * AI 超级智能体在 chat_memory 表中的应用类型。
     */
    private static final String MANUS_APP_TYPE = "manus";

    /**
     * 默认会话标题。当还没有找到第一条用户消息时，先使用这个占位标题。
     */
    private static final String DEFAULT_TITLE = "新对话";

    /**
     * 用户自定义会话标题的最大长度，和前端侧栏可读范围保持一致。
     */
    private static final int MAX_CONVERSATION_TITLE_LENGTH = 50;

    /**
     * 查询当前用户的 AI 股票大师历史会话列表。
     */
    @Override
    public List<StockConversationVO> listStockConversations(Long userId) {
        return listStockConversations(userId, null);
    }

    /**
     * 查询股票大师会话摘要。
     *
     * <p>chat_memory 按消息粒度存储，同一个 conversationId 下会有多条消息。
     * 这里会先把消息聚合成会话，再补充标题、更新时间，并按可选关键词过滤。</p>
     */
    @Override
    public List<StockConversationVO> listStockConversations(Long userId, String keyword) {
        // 先按应用类型和用户查询出所有有效消息，再在内存中按 conversationId 聚合为会话摘要。
        List<ChatMemory> rows = listConversationRows(userId, STOCK_APP_TYPE);

        // LinkedHashMap 保持遍历插入顺序，后面再按更新时间做最终排序。
        Map<String, StockConversationVO> conversationMap = new LinkedHashMap<>();
        // conversationContentMap 用来收集同一会话下的全部正文，支持侧边栏“按标题 / 内容搜索”。
        Map<String, StringBuilder> conversationContentMap = new LinkedHashMap<>();
        for (ChatMemory row : rows) {
            // computeIfAbsent 可以保证同一个 conversationId 只创建一个会话摘要对象。
            StockConversationVO conversation = conversationMap.computeIfAbsent(row.getConversationId(), conversationId -> {
                StockConversationVO vo = new StockConversationVO();
                vo.setConversationId(conversationId);
                vo.setTitle(getConversationTitle(row));
                return vo;
            });
            appendConversationContent(conversationContentMap, row);

            // 如果用户没有手动命名，则取第一条用户消息生成默认标题，方便侧栏快速识别对话主题。
            if (DEFAULT_TITLE.equals(conversation.getTitle()) && Objects.equals(row.getMessageType(), MessageType.USER.name())) {
                conversation.setTitle(buildConversationTitle(row.getContent()));
            }

            // 每个会话的 updateTime 取该会话所有消息里最新的一条。
            refreshStockConversationUpdateTime(conversation, row.getUpdateTime());
        }

        // 对会话列表进行关键词过滤和排序
        return conversationMap.values().stream()
                // 1. 根据标题和内容匹配关键词进行过滤
                .filter(conversation -> matchesConversationKeyword(
                        conversation.getTitle(),
                        conversationContentMap.get(conversation.getConversationId()),
                        keyword))
                // 2. 按更新时间降序排列（null值排在最后）
                .sorted((left, right) -> Comparator.nullsLast(Date::compareTo)
                        .compare(right.getUpdateTime(), left.getUpdateTime()))
                .toList();
    }

    /**
     * 查询某个 AI 股票大师会话的历史消息，用于前端回显。
     */
    @Override
    public List<StockMessageVO> getStockConversationMessages(String conversationId, Long userId) {
        return listMessageRows(conversationId, userId, STOCK_APP_TYPE).stream()
                // 前端聊天窗口只展示用户消息和 AI 回复，系统消息、工具消息都属于内部上下文。
                .filter(this::isVisibleMessage)
                .map(this::toStockMessageVO)
                .toList();
    }

    /**
     * 软删除当前用户的某个 AI 股票大师会话。
     */
    @Override
    public boolean deleteStockConversation(String conversationId, Long userId) {
        return softDeleteConversation(conversationId, userId, STOCK_APP_TYPE);
    }

    /**
     * 重命名指定的股票大师会话标题
     *
     * <p>此方法允许用户更改特定会话的标题，将新的标题信息存储到数据库的元数据字段中。
     * 标题会被验证并限制长度不超过50个字符，以确保数据一致性。</p>
     *
     * @param conversationId 会话唯一标识符，用于确定要重命名的具体会话
     * @param userId 用户唯一标识符，用于验证会话归属权
     * @param title 新的会话标题，不能为空且长度不能超过50个字符
     * @return 操作成功返回true，失败则返回false
     */
    @Override
    public boolean renameStockConversation(String conversationId, Long userId, String title) {
        return renameConversation(conversationId, userId, STOCK_APP_TYPE, title);
    }

    /**
     * 查询当前用户的 AI 超级智能体历史会话列表。
     */
    @Override
    public List<ManusConversationVO> listManusConversations(Long userId) {
        return listManusConversations(userId, null);
    }

    /**
     * 查询超级智能体会话摘要。
     *
     * <p>逻辑和股票大师会话列表一致，只是应用类型不同。保留两套 VO，
     * 可以让前端页面按各自业务继续扩展字段，而不互相影响。</p>
     */
    @Override
    public List<ManusConversationVO> listManusConversations(Long userId, String keyword) {
        // chat_memory 表按“消息”存储，这里先查出当前用户在超级智能体应用下的全部有效消息。
        // 后续再按 conversationId 聚合成前端侧边栏需要的“会话摘要”。
        List<ChatMemory> rows = listConversationRows(userId, MANUS_APP_TYPE);

        // conversationMap 的 key 是会话 id，value 是该会话最终返回给前端的摘要对象。
        // 使用 LinkedHashMap 可以保留查询结果的遍历顺序，方便在聚合过程中稳定写入标题和更新时间。
        Map<String, ManusConversationVO> conversationMap = new LinkedHashMap<>();
        // conversationContentMap 用来拼接同一会话下的所有消息正文。
        // 这样用户在侧边栏搜索时，不仅能搜标题，也能搜历史对话内容。
        Map<String, StringBuilder> conversationContentMap = new LinkedHashMap<>();
        for (ChatMemory row : rows) {
            // 超级智能体和股票大师共用 chat_memory 表，通过 appType 隔离不同应用的历史记录。
            // computeIfAbsent 表示：如果该 conversationId 第一次出现，就创建一个新的会话摘要；
            // 如果已经出现过，就继续复用之前创建的摘要对象。
            ManusConversationVO conversation = conversationMap.computeIfAbsent(row.getConversationId(), conversationId -> {
                ManusConversationVO vo = new ManusConversationVO();
                vo.setConversationId(conversationId);
                // 标题优先读取 metadata.title，也就是用户手动重命名后的标题；
                // 如果没有手动标题，会先放 DEFAULT_TITLE，后面再尝试用第一条用户消息生成标题。
                vo.setTitle(getConversationTitle(row));
                return vo;
            });
            // 把当前消息正文追加到该会话的内容缓存中，供 keyword 过滤使用。
            appendConversationContent(conversationContentMap, row);

            // 如果当前会话还没有自定义标题，并且当前行是用户消息，
            // 就用第一条用户消息内容生成一个默认标题，方便前端展示“这段对话大概在聊什么”。
            if (DEFAULT_TITLE.equals(conversation.getTitle()) && Objects.equals(row.getMessageType(), MessageType.USER.name())) {
                conversation.setTitle(buildConversationTitle(row.getContent()));
            }

            // 一个会话下会有多条消息，侧边栏的更新时间应该取该会话内最新一条消息的更新时间。
            refreshManusConversationUpdateTime(conversation, row.getUpdateTime());
        }

        return conversationMap.values().stream()
                // 根据 keyword 过滤会话；keyword 为空时保留全部会话。
                .filter(conversation -> matchesConversationKeyword(
                        conversation.getTitle(),
                        conversationContentMap.get(conversation.getConversationId()),
                        keyword))
                // 最近更新的会话排在最前面；null 时间放到最后，避免异常数据影响正常会话排序。
                .sorted((left, right) -> Comparator.nullsLast(Date::compareTo)
                        .compare(right.getUpdateTime(), left.getUpdateTime()))
                // Java 16+ 的 stream.toList() 返回不可变列表，适合直接作为查询结果返回。
                .toList();
    }

    /**
     * 查询某个 AI 超级智能体会话的历史消息，用于前端回显。
     */
    @Override
    public List<ManusMessageVO> getManusConversationMessages(String conversationId, Long userId) {
        return listMessageRows(conversationId, userId, MANUS_APP_TYPE).stream()
                .filter(this::isVisibleMessage)
                .map(this::toManusMessageVO)
                .toList();
    }

    /**
     * 软删除当前用户的某个 AI 超级智能体会话。
     */
    @Override
    public boolean deleteManusConversation(String conversationId, Long userId) {
        return softDeleteConversation(conversationId, userId, MANUS_APP_TYPE);
    }

    /**
     * 重命名指定的超级智能体会话标题
     *
     * <p>此方法允许用户更改特定会话的标题，将新的标题信息存储到数据库的元数据字段中。
     * 标题会被验证并限制长度不超过50个字符，以确保数据一致性。</p>
     *
     * @param conversationId 会话唯一标识符，用于确定要重命名的具体会话
     * @param userId 用户唯一标识符，用于验证会话归属权
     * @param title 新的会话标题，不能为空且长度不能超过50个字符
     * @return 操作成功返回true，失败则返回false
     */
    @Override
    public boolean renameManusConversation(String conversationId, Long userId, String title) {
        return renameConversation(conversationId, userId, MANUS_APP_TYPE, title);
    }

    /**
     * 查询 AI 超级智能体运行时需要恢复的历史上下文。
     */
    @Override
    public List<Message> getManusMessagesForAgent(String conversationId, Long userId) {
        return listMessageRows(conversationId, userId, MANUS_APP_TYPE).stream()
                .filter(this::isVisibleMessage)
                .map(this::toSpringAiMessage)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 保存 AI 超级智能体当前会话窗口中的可见消息。
     *
     * <p>这里使用“先软删除旧窗口，再插入新窗口”的方式，避免每轮对话结束后重复追加相同历史消息。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveManusMessages(String conversationId, Long userId, List<Message> messages) {
        // 超级智能体每次运行后会给出完整可见上下文，因此先软删旧窗口，再保存最新窗口。
        softDeleteConversation(conversationId, userId, MANUS_APP_TYPE);
        if (StrUtil.isBlank(conversationId) || userId == null || messages == null || messages.isEmpty()) {
            return;
        }

        List<ChatMemory> rows = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            ChatMemory row = toChatMemory(conversationId, userId, MANUS_APP_TYPE, messages.get(i), i);
            if (row != null) {
                rows.add(row);
            }
        }

        if (!rows.isEmpty()) {
            this.saveBatch(rows);
        }
    }

    /**
     * 查询某个应用下当前用户的所有有效消息，用于聚合成会话列表。
     */
    private List<ChatMemory> listConversationRows(Long userId, String appType) {
        LambdaQueryWrapper<ChatMemory> queryWrapper = new LambdaQueryWrapper<ChatMemory>()
                .eq(ChatMemory::getUserId, userId)
                .eq(ChatMemory::getAppType, appType)
                .eq(ChatMemory::getIsDelete, 0)
                // 先按会话分组顺序、再按消息顺序查询，方便后续在 Java 内存中做聚合。
                .orderByAsc(ChatMemory::getConversationId)
                .orderByAsc(ChatMemory::getMessageOrder)
                .orderByAsc(ChatMemory::getId);
        return this.list(queryWrapper);
    }

    /**
     * 查询某个会话下的有效消息，并按聊天顺序返回。
     */
    private List<ChatMemory> listMessageRows(String conversationId, Long userId, String appType) {
        LambdaQueryWrapper<ChatMemory> queryWrapper = new LambdaQueryWrapper<ChatMemory>()
                .eq(ChatMemory::getConversationId, conversationId)
                .eq(ChatMemory::getUserId, userId)
                .eq(ChatMemory::getAppType, appType)
                .eq(ChatMemory::getIsDelete, 0)
                .orderByAsc(ChatMemory::getMessageOrder)
                .orderByAsc(ChatMemory::getId);
        return this.list(queryWrapper);
    }

    /**
     * 对某个用户、某个应用、某个会话做软删除。
     */
    private boolean softDeleteConversation(String conversationId, Long userId, String appType) {
        if (StrUtil.isBlank(conversationId) || userId == null) {
            return false;
        }
        LambdaUpdateWrapper<ChatMemory> updateWrapper = new LambdaUpdateWrapper<ChatMemory>()
                // 必须显式 set 逻辑删除字段，否则 MyBatis-Plus 会生成没有 SET 的 UPDATE 语句。
                .set(ChatMemory::getIsDelete, 1)
                .eq(ChatMemory::getConversationId, conversationId)
                .eq(ChatMemory::getUserId, userId)
                .eq(ChatMemory::getAppType, appType)
                .eq(ChatMemory::getIsDelete, 0);
        return this.update(updateWrapper);
    }

    /**
     * 将自定义会话标题写入现有 metadata 字段，避免为了一个标题额外改表结构。
     */
    private boolean renameConversation(String conversationId, Long userId, String appType, String title) {
        if (StrUtil.isBlank(conversationId) || userId == null) {
            return false;
        }
        // 统一在服务层做标题清洗和长度限制，避免不同 Controller 写出不一致规则。
        String normalizedTitle = StrUtil.trim(title);
        if (StrUtil.isBlank(normalizedTitle)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话名称不能为空");
        }
        if (normalizedTitle.length() > MAX_CONVERSATION_TITLE_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话名称不能超过 50 个字符");
        }

        String metadataJson;
        try {
            // metadata 是 JSON 字段，这里只写入 title，后续还可以继续扩展置顶、标签等信息。
            metadataJson = OBJECT_MAPPER.writeValueAsString(Map.of("title", normalizedTitle));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }

        LambdaUpdateWrapper<ChatMemory> updateWrapper = new LambdaUpdateWrapper<ChatMemory>()
                .set(ChatMemory::getMetadata, metadataJson)
                .eq(ChatMemory::getConversationId, conversationId)
                .eq(ChatMemory::getUserId, userId)
                .eq(ChatMemory::getAppType, appType)
                .eq(ChatMemory::getIsDelete, 0);
        return this.update(updateWrapper);
    }

    /**
     * 判断某条数据库消息是否应该展示给用户和恢复给 AI。
     */
    private boolean isVisibleMessage(ChatMemory row) {
        // 只把用户消息和 AI 回复展示给前端；系统提示词和工具消息属于内部编排信息。
        return Objects.equals(row.getMessageType(), MessageType.USER.name())
                || Objects.equals(row.getMessageType(), MessageType.ASSISTANT.name());
    }

    /**
     * 把 Spring AI Message 转成 chat_memory 表实体。
     */
    private ChatMemory toChatMemory(String conversationId, Long userId, String appType, Message message, int messageOrder) {
        // 工具消息属于智能体内部执行细节，暂不落到用户可见的聊天历史中。
        if (message == null || message.getMessageType() == MessageType.TOOL || StrUtil.isBlank(message.getText())) {
            return null;
        }
        ChatMemory chatMemory = new ChatMemory();
        chatMemory.setConversationId(conversationId);
        chatMemory.setUserId(userId);
        chatMemory.setAppType(appType);
        chatMemory.setMessageType(message.getMessageType().name());
        chatMemory.setContent(message.getText());
        chatMemory.setMessageOrder(messageOrder);
        chatMemory.setIsDelete(0);
        return chatMemory;
    }

    /**
     * 把数据库消息还原成 Spring AI Message，供 AI 超级智能体恢复上下文。
     */
    private Message toSpringAiMessage(ChatMemory chatMemory) {
        if (chatMemory == null || StrUtil.isBlank(chatMemory.getContent())) {
            return null;
        }
        try {
            // 只恢复 Spring AI 能直接消费的消息类型；无法识别的历史记录直接跳过。
            MessageType messageType = MessageType.valueOf(chatMemory.getMessageType());
            return switch (messageType) {
                case USER -> new UserMessage(chatMemory.getContent());
                case ASSISTANT -> new AssistantMessage(chatMemory.getContent());
                case SYSTEM -> new SystemMessage(chatMemory.getContent());
                case TOOL -> null;
            };
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 把数据库消息转成股票大师前端消息 VO。
     */
    private StockMessageVO toStockMessageVO(ChatMemory chatMemory) {
        StockMessageVO stockMessageVO = new StockMessageVO();
        stockMessageVO.setContent(chatMemory.getContent());
        stockMessageVO.setIsUser(Objects.equals(chatMemory.getMessageType(), MessageType.USER.name()));
        stockMessageVO.setTime(toTimestamp(chatMemory.getCreateTime()));
        return stockMessageVO;
    }

    /**
     * 把数据库消息转成超级智能体前端消息 VO。
     */
    private ManusMessageVO toManusMessageVO(ChatMemory chatMemory) {
        ManusMessageVO manusMessageVO = new ManusMessageVO();
        manusMessageVO.setContent(chatMemory.getContent());
        manusMessageVO.setIsUser(Objects.equals(chatMemory.getMessageType(), MessageType.USER.name()));
        manusMessageVO.setTime(toTimestamp(chatMemory.getCreateTime()));
        return manusMessageVO;
    }

    /**
     * 刷新股票大师会话摘要里的最近更新时间。
     */
    private void refreshStockConversationUpdateTime(StockConversationVO conversation, Date rowUpdateTime) {
        if (rowUpdateTime != null
                && (conversation.getUpdateTime() == null || rowUpdateTime.after(conversation.getUpdateTime()))) {
            conversation.setUpdateTime(rowUpdateTime);
        }
    }

    /**
     * 刷新超级智能体会话摘要里的最近更新时间。
     */
    private void refreshManusConversationUpdateTime(ManusConversationVO conversation, Date rowUpdateTime) {
        if (rowUpdateTime != null
                && (conversation.getUpdateTime() == null || rowUpdateTime.after(conversation.getUpdateTime()))) {
            conversation.setUpdateTime(rowUpdateTime);
        }
    }

    /**
     * 把 Date 转成前端更容易处理的毫秒时间戳。
     */
    private Long toTimestamp(Date date) {
        return date == null ? System.currentTimeMillis() : date.getTime();
    }

    /**
     * 获取会话标题时优先读取用户手动设置的 metadata.title。
     */
    private String getConversationTitle(ChatMemory row) {
        String customTitle = extractCustomTitle(row.getMetadata());
        return StrUtil.isNotBlank(customTitle) ? customTitle : DEFAULT_TITLE;
    }

    /**
     * 从 metadata 中解析自定义标题。
     *
     * <p>不同 JDBC / JSON 类型处理方式下，metadata 可能被还原成 Map，也可能是 JSON 字符串，
     * 所以这里同时兼容两种形态。</p>
     */
    private String extractCustomTitle(Object metadata) {
        if (metadata == null) {
            return null;
        }
        if (metadata instanceof Map<?, ?> metadataMap) {
            // 某些数据库驱动会直接把 JSON 字段反序列化成 Map，这种情况无需再次解析字符串。
            Object title = metadataMap.get("title");
            return title == null ? null : StrUtil.trim(String.valueOf(title));
        }
        String metadataText = String.valueOf(metadata);
        if (StrUtil.isBlank(metadataText)) {
            return null;
        }
        try {
            // 如果 metadata 是普通 JSON 字符串，则用 ObjectMapper 解析并读取 title 字段。
            Map<String, Object> metadataMap = OBJECT_MAPPER.readValue(
                    metadataText, new TypeReference<Map<String, Object>>() {
                    });
            Object title = metadataMap.get("title");
            return title == null ? null : StrUtil.trim(String.valueOf(title));
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 汇总同一会话下的全部消息正文，用于侧边栏按“标题 / 内容”搜索。
     */
    private void appendConversationContent(Map<String, StringBuilder> conversationContentMap, ChatMemory row) {
        if (row == null || StrUtil.isBlank(row.getConversationId()) || StrUtil.isBlank(row.getContent())) {
            return;
        }
        conversationContentMap.computeIfAbsent(row.getConversationId(), key -> new StringBuilder())
                .append(' ')
                .append(row.getContent());
    }

    /**
     * 判断某个会话是否命中搜索关键词。
     */
    private boolean matchesConversationKeyword(String title, StringBuilder contentBuilder, String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return true;
        }
        // 统一转小写后匹配，降低用户搜索时大小写不一致带来的干扰。
        String normalizedKeyword = keyword.trim().toLowerCase();
        String normalizedTitle = StrUtil.blankToDefault(title, "").toLowerCase();
        String normalizedContent = contentBuilder == null ? "" : contentBuilder.toString().toLowerCase();
        return normalizedTitle.contains(normalizedKeyword) || normalizedContent.contains(normalizedKeyword);
    }

    /**
     * 根据用户第一条消息生成会话标题。
     */
    private String buildConversationTitle(String content) {
        if (StrUtil.isBlank(content)) {
            return DEFAULT_TITLE;
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() > 24 ? normalized.substring(0, 24) + "..." : normalized;
    }
}
