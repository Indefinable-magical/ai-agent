package com.yu.aiagent.rag.retrieval;

import com.yu.aiagent.rag.transformer.StockQueryTransformResult;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 自定义 RAG 检索上下文。
 *
 * <p>一次用户问题经过 QueryTransformer 和混合检索后，最终会被整理成这个对象。
 * StockAppRagAdvisor 会把 contextText 注入 Prompt，同时把整个对象放进 request context。</p>
 */
@Data
@Builder
public class StockRagContext {

    /**
     * QueryTransformer 处理结果，包括原始问题、改写问题、扩展关键词、意图。
     */
    private StockQueryTransformResult query;

    /**
     * 混合检索命中的资料列表。
     */
    private List<StockRagDocument> documents;

    /**
     * 是否命中有效知识。
     */
    private boolean hasContext;

    /**
     * 已经拼接好的上下文文本，可以直接追加到用户消息中。
     */
    private String contextText;
}
