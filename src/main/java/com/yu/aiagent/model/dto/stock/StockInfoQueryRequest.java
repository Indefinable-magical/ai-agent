package com.yu.aiagent.model.dto.stock;

import com.yu.aiagent.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 股票池查询请求。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class StockInfoQueryRequest extends PageRequest implements Serializable {

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
     * 主题关键词。
     */
    private String themeKeyword;

    /**
     * 风险关键词。
     */
    private String riskKeyword;

    /**
     * 入池理由关键词。
     */
    private String reasonKeyword;

    private static final long serialVersionUID = 1L;
}
