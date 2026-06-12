package com.yu.aiagent.rag;

import com.yu.aiagent.rag.retrieval.StockHybridRetrievalService;
import com.yu.aiagent.rag.retrieval.StockRagContext;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 股票大师自定义 RAG Advisor。
 *
 * <p>Advisor 是 Spring AI 在模型调用前后插入增强逻辑的扩展点。
 * 这个类的职责是在真正请求大模型之前，先执行自定义混合检索，
 * 再把检索到的股票知识追加到用户消息后面，让模型带着外部知识回答。</p>
 */
@Component
public class StockAppRagAdvisor implements BaseAdvisor {

    /**
     * 把本次 RAG 检索结果放入 ChatClientRequest 的 context 中时使用的 key。
     *
     * <p>当前主要用于调试和后续扩展。如果以后要在日志、前端或响应后处理里查看
     * 本次命中了哪些资料，可以从 context 里按这个 key 取出 StockRagContext。</p>
     */
    public static final String RAG_CONTEXT_KEY = "stockRagContext";

    /**
     * 混合检索服务，内部会串联：
     * 问题改写、关键词扩展、意图识别、向量库检索、MySQL 检索、Redis 热门缓存。
     */
    private final StockHybridRetrievalService stockHybridRetrievalService;

    public StockAppRagAdvisor(StockHybridRetrievalService stockHybridRetrievalService) {
        this.stockHybridRetrievalService = stockHybridRetrievalService;
    }

    /**
     * 模型调用前执行。
     *
     * <p>执行顺序是：</p>
     * <p>1. 从当前 Prompt 里拿到用户原始问题。</p>
     * <p>2. 调用混合检索服务生成 RAG 上下文。</p>
     * <p>3. 把 RAG 上下文追加到用户消息后面。</p>
     * <p>4. 把完整检索结果放入 request context，便于后续扩展使用。</p>
     */
    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 取出当前请求中的用户消息文本，作为 RAG 检索的原始输入。
        String userText = chatClientRequest.prompt().getUserMessage().getText();

        // 执行自定义混合检索，得到适合注入 Prompt 的上下文文本。
        StockRagContext ragContext = stockHybridRetrievalService.retrieve(userText);

        // Spring AI 的 Prompt 是不可变风格，这里通过 augmentUserMessage 生成增强后的 Prompt。
        Prompt augmentedPrompt = chatClientRequest.prompt().augmentUserMessage("""

                ---
                %s
                """.formatted(ragContext.getContextText()));

        // 复制原有 context，避免覆盖其他 Advisor 已经放进去的上下文参数。
        Map<String, Object> context = new LinkedHashMap<>(chatClientRequest.context());

        // 保存完整 RAG 结果，后续如果要展示“命中文档”或做日志分析，可以直接取用。
        context.put(RAG_CONTEXT_KEY, ragContext);

        // 返回新的请求对象，让后续 Advisor 和模型调用使用增强后的 Prompt。
        return new ChatClientRequest(augmentedPrompt, context);
    }

    /**
     * 模型调用后执行。
     *
     * <p>当前不改写模型响应，只原样返回。保留这个方法是为了以后扩展：
     * 比如追加来源列表、清洗格式、统计 token、记录 RAG 命中情况等。</p>
     */
    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    /**
     * Advisor 执行顺序。
     *
     * <p>这里让 RAG Advisor 靠后执行，确保登录态、记忆、违禁词等基础 Advisor
     * 先完成自己的工作，然后再把检索上下文注入到最终请求里。</p>
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}
