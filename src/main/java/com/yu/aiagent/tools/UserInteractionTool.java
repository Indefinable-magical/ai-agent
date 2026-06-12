package com.yu.aiagent.tools;

import cn.hutool.core.util.StrUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 用户交互工具。
 *
 * <p>当智能体缺少必要信息、需要用户确认方向，或继续执行前需要反馈时，
 * 可以调用该工具把问题明确抛给用户。后端会暂停本轮执行，等待用户下一轮回复。</p>
 */
public class UserInteractionTool {

    /**
     * 向用户发起一次澄清问题。
     *
     * <p>这个工具本身不负责等待输入，只负责把“需要用户回答的问题”返回给 Agent。
     * Agent 识别到该工具被调用后，会把状态切换为 WAITING_USER，并结束当前 SSE 流；
     * 用户下一次发送消息时，会带着历史上下文继续执行。</p>
     *
     * @param question 需要展示给用户的问题
     * @return 规范化后的问题文本
     */
    @Tool(description = """
            Ask the user for missing information, confirmation, or feedback before continuing.
            Use this when the task is ambiguous, required inputs are missing, or continuing may waste steps.
            Ask one clear question in Chinese by default.
            """)
    public String askUser(@ToolParam(description = "A clear question to ask the user") String question) {
        // 给一个兜底问题，避免模型传空参数时前端没有可展示内容。
        return StrUtil.blankToDefault(question, "我需要你补充一些信息后才能继续。");
    }
}
