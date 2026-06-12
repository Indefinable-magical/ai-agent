package com.yu.aiagent.app;

import com.yu.aiagent.advisor.MyLoggerAdvisor;
import com.yu.aiagent.advisor.SensitiveWordAdvisor;
import com.yu.aiagent.chatmemory.MysqlChatMemoryRepository;
import com.yu.aiagent.prompt.PromptTemplateLoader;
import com.yu.aiagent.rag.StockAppRagAdvisor;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * AI 股票大师应用入口。
 */
@Component
@Slf4j
public class StockApp {

    private final ChatClient chatClient;
    private final PromptTemplateLoader promptTemplateLoader;
    private final String stockSystemPrompt;

    public StockApp(ChatModel dashscopeChatModel,
                    PromptTemplateLoader promptTemplateLoader,
                    MysqlChatMemoryRepository mysqlChatMemoryRepository) {
        this.promptTemplateLoader = promptTemplateLoader;
        this.stockSystemPrompt = promptTemplateLoader.loadPrompt("stock-system.st");
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(mysqlChatMemoryRepository)
                .maxMessages(20)
                .build();
        this.chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(stockSystemPrompt)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        // 违禁词 Advisor，在请求模型前进行内容校验
                        new SensitiveWordAdvisor(),
                        // 自定义日志 Advisor，可按需开启
                        new MyLoggerAdvisor()
                )
                .build();
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    @Resource
    private StockAppRagAdvisor stockAppRagAdvisor;

    /**
     * AI 股票大师流式对话：对话记忆 + 自定义 RAG Advisor。
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        return doChatByStream(message, chatId, null);
    }

    /**
     * AI 股票大师流式对话：对话记忆 + 自定义 RAG Advisor + 用户股票偏好上下文。
     *
     * <p>用户股票偏好上下文来自自选股和风险偏好，只在本次请求覆盖 system prompt，
     * 不会改动 ChatClient 默认配置。</p>
     */
    public Flux<String> doChatByStream(String message, String chatId, String stockUserContext) {
        String systemPrompt = StrUtil.isBlank(stockUserContext)
                ? stockSystemPrompt
                : stockSystemPrompt + "\n\n[用户股票偏好上下文]\n" + stockUserContext;
        return chatClient
                .prompt()
                .system(systemPrompt)
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(stockAppRagAdvisor)
                .stream()
                .content();
    }

    record StockReport(String title, List<String> suggestions) {
    }

    /**
     * AI 股票分析报告功能（实战结构化输出）
     *
     * @param message
     * @param chatId
     * @return
     */
    public StockReport doChatWithReport(String message, String chatId) {
        String reportSystemPrompt = promptTemplateLoader.renderPrompt("stock-report-system.st", Map.of(
                "basePrompt", stockSystemPrompt,
                "userName", "用户"
        ));
        StockReport stockReport = chatClient
                .prompt()
                .system(reportSystemPrompt)
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(StockReport.class);
        log.info("stockReport: {}", stockReport);
        return stockReport;
    }

    /**
     * RAG 问答：使用自定义 RAG Advisor 统一完成改写、检索、分组、兜底和来源引用。
     */
    public String doChatWithRag(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .advisors(stockAppRagAdvisor)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // AI 调用工具能力
    @Resource
    private ToolCallback[] allTools;

    /**
     * AI 股票分析功能（支持调用工具）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithTools(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // AI 调用 MCP 服务

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * AI 股票分析功能（调用 MCP 服务）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithMcp(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}
