package com.yu.aiagent.model.dto.stock;

import lombok.Data;

import java.io.Serializable;

/**
 * 添加自选股请求
 */
@Data
public class StockWatchlistAddRequest implements Serializable {

    /**
     * 股票代码
     */
    private String stockCode;

    /**
     * 股票名称
     */
    private String stockName;

    /**
     * 关注理由或备注
     */
    private String remark;

    private static final long serialVersionUID = 1L;
}
