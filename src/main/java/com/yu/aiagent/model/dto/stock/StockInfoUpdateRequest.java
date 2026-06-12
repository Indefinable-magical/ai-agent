package com.yu.aiagent.model.dto.stock;

import lombok.Data;

import java.io.Serializable;

/**
 * 股票池更新请求。
 */
@Data
public class StockInfoUpdateRequest implements Serializable {

    /**
     * 股票记录 id。
     */
    private Long id;

    /**
     * 股票代码。
     */
    private String stockCode;

    /**
     * 股票名称。
     */
    private String stockName;

    /**
     * 市场。
     */
    private String market;

    /**
     * 所属行业。
     */
    private String industry;

    /**
     * 主题标签。
     */
    private String themes;

    /**
     * 风险标签。
     */
    private String riskTags;

    /**
     * 入池理由。
     */
    private String reason;

    /**
     * 热度分。
     */
    private Integer hotScore;

    private static final long serialVersionUID = 1L;
}
