package com.yu.aiagent.rag.retrieval;

import lombok.Builder;
import lombok.Data;

/**
 * 混合检索返回的统一文档结构。
 *
 * <p>向量库返回的是 Spring AI Document，MySQL 返回的是 StockInfo，
 * Redis 返回的是热点字符串。为了后续统一排序、过滤、分组、拼 Prompt，
 * 这里把不同来源的数据统一包装成 StockRagDocument。</p>
 */
@Data
@Builder
public class StockRagDocument {

    /**
     * 来源类型，例如 markdown、csv_stock_pool、pdf_announcement、web_research、mysql、redis。
     */
    private String sourceType;

    /**
     * 来源名称，例如文件名、表名、Redis key。
     */
    private String sourceName;

    /**
     * 内容分组，例如股票、行业、主题、风险。
     */
    private String groupName;

    /**
     * 可以直接注入 Prompt 的正文内容。
     */
    private String content;

    /**
     * 排序权重。权重越高，越优先进入最终 RAG 上下文。
     */
    private double weight;

    /**
     * 风险等级：low、medium、high。
     */
    private String riskLevel;
}
