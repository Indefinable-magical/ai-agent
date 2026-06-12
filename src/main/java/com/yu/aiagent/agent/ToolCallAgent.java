package com.yu.aiagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.yu.aiagent.agent.model.AgentState;
import com.yu.aiagent.exception.BusinessException;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 可用的工具
    private final ToolCallback[] availableTools;

    // 保存工具调用信息的响应结果（要调用那些工具）
    private ChatResponse toolCallChatResponse;

    // 模型未选择工具时的直接回复
    private String directResponse;

    // 当前步骤中模型给用户看的自然语言回复
    private String currentStepResponse;

    // 当前步骤的工具调用签名，用于循环检测。相比直接使用模型回复文本，工具名 + 参数更能代表真实动作。
    private String currentToolCallSignature;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
    private final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {
        this.currentStepResponse = null;
        this.currentToolCallSignature = null;
        // 1、校验提示词，拼接用户提示词
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }
        // 2、调用 AI 大模型，获取工具调用结果
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();
            // 记录响应，用于等下 Act
            this.toolCallChatResponse = chatResponse;
            // 3、解析工具调用结果，获取要调用的工具
            // 助手消息
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // 获取要调用的工具列表
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            // 输出提示信息
            String result = assistantMessage.getText();
            this.currentStepResponse = result;
            emitStepEvent(getCurrentStep(), "thinking", "模型已完成思考",
                    StrUtil.blankToDefault(result, "正在根据上下文选择下一步动作"), "success");
            log.info(getName() + "的思考：" + result);
            log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s", toolCall.name(), toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);
            this.currentToolCallSignature = buildToolCallSignature(toolCallList);
            // 如果不需要调用工具，返回 false
            if (toolCallList.isEmpty()) {
                // 只有不调用工具时，才需要手动记录助手消息
                getMessageList().add(assistantMessage);
                this.directResponse = StrUtil.blankToDefault(result, "思考完成，无需调用工具。");
                emitStepEvent(getCurrentStep(), "generating_report", "生成最终回复",
                        "没有新的工具调用，正在整理可直接返回的回答", "running");
                setState(AgentState.FINISHED);
                return false;
            } else {
                // 将模型选择的工具提前推送给前端，用户可以看到 Agent 接下来准备做什么。
                toolCallList.forEach(toolCall -> emitStepEvent(getCurrentStep(), resolveToolEventType(toolCall.name()),
                        "准备调用工具：" + toolCall.name(), summarizeToolArguments(toolCall.arguments()), "running"));
                // 需要调用工具时，无需记录助手消息，因为调用工具时会自动记录
                return true;
            }
        } catch (Exception e) {
            if (e instanceof BusinessException) {
                log.warn("{}的思考过程被业务规则拦截：{}", getName(), e.getMessage());
                this.directResponse = e.getMessage();
                getMessageList().add(new AssistantMessage(this.directResponse));
                emitStepEvent(getCurrentStep(), "error", "业务规则拦截", e.getMessage(), "error");
                setState(AgentState.FINISHED);
                return false;
            }
            log.error(getName() + "的思考过程遇到了问题：" + e.getMessage(), e);
            this.directResponse = "处理时遇到了错误：" + e.getMessage();
            getMessageList().add(new AssistantMessage(this.directResponse));
            emitStepEvent(getCurrentStep(), "error", "思考过程异常", e.getMessage(), "error");
            setState(AgentState.ERROR);
            return false;
        }
    }

    @Override
    protected String getNoActionResult() {
        return StrUtil.blankToDefault(directResponse, "思考完成，无需行动。");
    }

    /**
     * 执行工具调用并处理结果
     *
     * @return 执行结果
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }
        emitStepEvent(getCurrentStep(), "calling_tool", "正在执行工具", "工具调用已开始，请稍候", "running");
        // 调用工具
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        // 记录消息上下文，conversationHistory 已经包含了助手消息和工具调用返回的结果
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        // askUser 是交互式执行的关键工具：命中后本轮不继续调用其他工具，而是等待用户补充信息。
        boolean askUserToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("askUser"));
        // 判断是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("doTerminate"));
        if (askUserToolCalled) {
            setState(AgentState.WAITING_USER);
        } else if (terminateToolCalled) {
            // 任务结束，更改状态
            setState(AgentState.FINISHED);
        }
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 返回的结果：" + response.responseData())
                .collect(Collectors.joining("\n"));
        log.info(results);
        toolResponseMessage.getResponses().forEach(response -> emitStepEvent(getCurrentStep(),
                resolveToolEventType(response.name()), "工具执行完成：" + response.name(),
                summarizeToolResult(response.responseData()), "success"));
        if (askUserToolCalled) {
            // 取出 askUser 工具返回的问题文本，作为本轮最终可见回复。
            String question = toolResponseMessage.getResponses().stream()
                    .filter(response -> response.name().equals("askUser"))
                    .map(ToolResponseMessage.ToolResponse::responseData)
                    .findFirst()
                    .orElse("我需要你补充一些信息后才能继续。");
            String finalResponse = StrUtil.blankToDefault(currentStepResponse, question);
            if (!finalResponse.contains(question)) {
                // 如果模型自然语言回复没有包含问题，就直接使用工具返回的问题，避免用户看不懂要补什么。
                finalResponse = question;
            }
            getMessageList().add(new AssistantMessage(finalResponse));
            emitStepEvent(getCurrentStep(), "interaction_required", "需要用户反馈",
                    summarizeToolResult(finalResponse), "running");
            return finalResponse;
        }
        if (terminateToolCalled) {
            String finalResponse = StrUtil.blankToDefault(currentStepResponse, results);
            getMessageList().add(new AssistantMessage(finalResponse));
            emitStepEvent(getCurrentStep(), "generating_report", "生成最终回复", "任务已满足终止条件，正在整理最终结果", "success");
            return finalResponse;
        }
        return "";
    }

    /**
     * 工具型 Agent 使用工具调用签名做循环检测。
     *
     * <p>例如连续多轮都调用 readFile 并传入同一个文件名，就会被识别为重复动作。</p>
     */
    @Override
    protected String buildLoopSignature(String stepResult) {
        return StrUtil.blankToDefault(currentToolCallSignature, "");
    }

    /**
     * 根据工具名称归类事件类型，前端会用 type 决定时间线图标和文案风格。
     */
    private String resolveToolEventType(String toolName) {
        String normalizedName = StrUtil.blankToDefault(toolName, "").toLowerCase();
        if (normalizedName.contains("search") || normalizedName.contains("web")) {
            return "searching";
        }
        if (normalizedName.contains("read") || normalizedName.contains("file")) {
            return "reading_file";
        }
        if (normalizedName.contains("terminate") || normalizedName.contains("report")) {
            return "generating_report";
        }
        if (normalizedName.contains("askuser") || normalizedName.contains("interaction")) {
            return "interaction_required";
        }
        return "calling_tool";
    }

    /**
     * 将同一步选择的工具名称和参数压缩成稳定签名。
     *
     * <p>同一步可能选择多个工具，所以这里把所有工具调用按顺序拼接。
     * 只要模型连续多步选择完全相同的工具和参数，就会触发 BaseAgent 的循环保护。</p>
     */
    private String buildToolCallSignature(List<AssistantMessage.ToolCall> toolCallList) {
        if (CollUtil.isEmpty(toolCallList)) {
            return "";
        }
        return toolCallList.stream()
                .map(toolCall -> toolCall.name() + ":" + StrUtil.blankToDefault(toolCall.arguments(), ""))
                .collect(Collectors.joining("|"));
    }

    /**
     * 工具参数可能较长，只截取摘要展示在时间线里，避免侧栏被大段 JSON 撑开。
     */
    private String summarizeToolArguments(String arguments) {
        return summarizeText(arguments, "正在准备工具调用参数");
    }

    /**
     * 工具返回结果可能包含大量正文，只在过程时间线展示短摘要，完整内容仍交给模型继续处理。
     */
    private String summarizeToolResult(String result) {
        return summarizeText(result, "工具已返回结果");
    }

    private String summarizeText(String text, String defaultText) {
        String normalizedText = StrUtil.blankToDefault(text, defaultText).replaceAll("\\s+", " ").trim();
        if (normalizedText.length() <= 120) {
            return normalizedText;
        }
        return normalizedText.substring(0, 120) + "...";
    }
}
