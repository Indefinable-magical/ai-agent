package com.yu.aiagent.model.vo;

import lombok.Data;

import java.util.Date;

/**
 * 用户自选股视图。
 */
@Data
public class StockWatchlistVO {

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

    /**
     * 添加时间
     */
    private Date createTime;
}
