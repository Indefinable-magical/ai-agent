package com.yu.aiagent.chatmemory;

import cn.hutool.core.util.StrUtil;
import com.yu.aiagent.agent.YuManus;
import com.yu.aiagent.service.ChatMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AI 超级智能体的 MySQL 会话记忆适配器。
 *
 * <p>YuManus 不是直接使用 Spring AI 的 MessageWindowChatMemory，
 * 它的上下文由 BaseAgent#messageList 自己维护，所以这里单独负责两件事：
 * 运行前把 MySQL 历史消息恢复到 Agent，运行后把用户可见的聊天消息保存回 MySQL。</p>
 */
@Component
@RequiredArgsConstructor
public class ManusChatMemoryManager {

    /**
     * 对话记忆业务服务，真正的数据读写都交给它完成。
     */
    private final ChatMemoryService chatMemoryService;

    /**
     * 给本次 YuManus 实例挂载 MySQL 记忆。
     *
     * @param manus          当前请求创建的 AI 超级智能体实例
     * @param conversationId 前端传入的会话 id
     * @param userId         当前登录用户 id
     */
    public void attachMemory(YuManus manus, String conversationId, Long userId) {
        List<Message> historyMessages = chatMemoryService.getManusMessagesForAgent(conversationId, userId);
        manus.setMessageList(new ArrayList<>(historyMessages));
        manus.setMessageListSaver(messages -> saveVisibleMessages(manus, conversationId, userId, messages));
    }

    /**
     * 保存用户真正能看到的消息，过滤掉 Agent 为了自主规划而追加的内部下一步提示和工具消息。
     */
    private void saveVisibleMessages(YuManus manus, String conversationId, Long userId, List<Message> messages) {
        if (StrUtil.isBlank(conversationId) || userId == null || messages == null) {
            return;
        }
        List<Message> visibleMessages = messages.stream()
                .filter(Objects::nonNull)
                .filter(message -> message.getMessageType() != MessageType.TOOL)
                .filter(message -> StrUtil.isNotBlank(message.getText()))
                .filter(message -> !isAssistantToolCallMessage(message))
                .filter(message -> !isInternalNextStepPrompt(manus, message))
                .toList();
        chatMemoryService.saveManusMessages(conversationId, userId, visibleMessages);
    }

    /**
     * 判断消息是否为 YuManus 每一步思考前追加的内部提示词。
     */
    private boolean isInternalNextStepPrompt(YuManus manus, Message message) {
        return message instanceof UserMessage
                && Objects.equals(message.getText(), manus.getNextStepPrompt());
    }

    /**
     * 判断消息是否为模型为了调用工具而生成的助手消息。
     */
    private boolean isAssistantToolCallMessage(Message message) {
        return message instanceof AssistantMessage assistantMessage
                && !assistantMessage.getToolCalls().isEmpty();
    }
}
