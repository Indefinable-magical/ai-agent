package com.yu.aiagent.model.dto.stock;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新自选股请求
 */
@Data
public class StockWatchlistUpdateRequest implements Serializable {

    /**
     * 自选股记录 id
     */
    private Long id;

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
