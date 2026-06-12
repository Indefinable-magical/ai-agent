package com.yu.aiagent.agent;

import com.yu.aiagent.advisor.MyLoggerAdvisor;
import com.yu.aiagent.advisor.SensitiveWordAdvisor;
import com.yu.aiagent.prompt.PromptTemplateLoader;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * AI 超级智能体。
 *
 * <p>YuManus 是当前项目里的“多步骤自主规划型 Agent”。它继承 {@link ToolCallAgent}，
 * 因此具备以下能力：</p>
 * <ul>
 *     <li>维护多轮消息上下文。</li>
 *     <li>让模型判断当前步骤是否需要调用工具。</li>
 *     <li>执行工具调用，并把工具结果继续放回上下文。</li>
 *     <li>循环执行多步任务，直到模型调用终止工具或达到最大步数。</li>
 * </ul>
 *
 * <p>注意：这个类只负责组装 YuManus 的专属配置，比如 Prompt、最大步骤数、
 * ChatClient 和 Advisor。真正的执行循环在 {@link BaseAgent}，
 * 工具调用逻辑在 {@link ToolCallAgent}。</p>
 */
@Component
public class YuManus extends ToolCallAgent {

    /**
     * 创建一个 AI 超级智能体实例。
     *
     * <p>Controller 每次收到超级智能体聊天请求时，都会创建一个新的 YuManus 实例。
     * 这样可以保证不同请求之间的运行状态、步骤数和 messageList 不会互相污染。</p>
     *
     * @param allTools             当前系统注册的全部工具回调，供模型按需选择调用
     * @param dashscopeChatModel   DashScope 聊天模型，作为底层大模型能力
     * @param promptTemplateLoader Prompt 模板加载器，用于从 resources/prompts 读取提示词
     */
    public YuManus(ToolCallback[] allTools,
                   ChatModel dashscopeChatModel,
                   PromptTemplateLoader promptTemplateLoader) {
        // 把所有可用工具交给父类 ToolCallAgent，后续模型选择工具时会从这里取。
        super(allTools);

        // 设置 Agent 名称，主要用于日志输出和排查问题。
        this.setName("YuManus");

        // 从资源文件加载系统提示词，定义超级智能体的角色、能力边界和总体行为规则。
        String systemPrompt = promptTemplateLoader.loadPrompt("manus-system.st");
        this.setSystemPrompt(systemPrompt);

        // 从资源文件加载每一步的追问提示词，用来引导模型继续规划下一步或结束任务。
        String nextStepPrompt = promptTemplateLoader.loadPrompt("manus-next-step.st");
        this.setNextStepPrompt(nextStepPrompt);

        // 超级智能体可能需要多次思考和工具调用，所以最大步骤数比普通对话更高。
        this.setMaxSteps(20);

        // 构建 Spring AI ChatClient，并为 YuManus 配置默认 Advisor。
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(
                        // 先做输入合规检查，命中违禁词时直接拦截，避免请求进入大模型。
                        new SensitiveWordAdvisor(),
                        // 记录请求和响应日志，方便调试 Agent 的思考与工具调用过程。
                        new MyLoggerAdvisor()
                )
                .build();

        // 把配置好的 ChatClient 注入到 BaseAgent，后续 ToolCallAgent 会用它调用模型。
        this.setChatClient(chatClient);
    }
}
