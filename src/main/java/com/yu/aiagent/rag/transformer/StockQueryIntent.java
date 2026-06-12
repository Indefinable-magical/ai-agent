package com.yu.aiagent.rag.transformer;

/**
 * 股票问题意图分类。
 *
 * <p>意图识别的结果会被后续混合检索使用：
 * 比如风险分析问题可以允许更多风险资料进入上下文；
 * 普通找股票问题则要更谨慎地过滤高风险标的。</p>
 */
public enum StockQueryIntent {

    /**
     * 用户想寻找可能关注的股票标的。
     */
    STOCK_DISCOVERY,

    /**
     * 用户想研究行业或产业链。
     */
    INDUSTRY_RESEARCH,

    /**
     * 用户想研究投资主题、市场主线或题材方向。
     */
    THEME_RESEARCH,

    /**
     * 用户想分析风险、回撤、退潮、估值等问题。
     */
    RISK_ANALYSIS,

    /**
     * 用户想分析具体公司。
     */
    COMPANY_ANALYSIS,

    /**
     * 兜底意图，表示没有识别出特别明确的股票问题类型。
     */
    GENERAL
}
