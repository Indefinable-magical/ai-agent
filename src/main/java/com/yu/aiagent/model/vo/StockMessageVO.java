package com.yu.aiagent.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 股票大师历史消息视图。
 */
@Data
public class StockMessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息正文。
     */
    private String content;

    /**
     * 是否为用户消息。
     */
    private Boolean isUser;

    /**
     * 前端展示用时间戳。
     */
    private Long time;
}
