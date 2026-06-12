package com.yu.aiagent.rag.transformer;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 股票问题转换结果。
 *
 * <p>StockQueryTransformer 不只是返回一个字符串，而是返回完整的转换结果。
 * 这样后续向量检索可以使用 rewrittenQuery，MySQL 检索可以使用 expandedKeywords，
 * 风险过滤可以使用 intent。</p>
 */
@Data
@Builder
public class StockQueryTransformResult {

    /**
     * 用户原始问题。
     */
    private String originalQuery;

    /**
     * 改写后的检索问题。
     */
    private String rewrittenQuery;

    /**
     * 扩展出的领域关键词。
     */
    private List<String> expandedKeywords;

    /**
     * 识别出的用户意图。
     */
    private StockQueryIntent intent;
}
