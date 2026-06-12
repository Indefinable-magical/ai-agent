package com.yu.aiagent.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * AI 股票大师会话摘要视图。
 */
@Data
public class StockConversationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话 id。
     */
    private String conversationId;

    /**
     * 会话标题，默认取首条用户消息生成。
     */
    private String title;

    /**
     * 最近更新时间。
     */
    private Date updateTime;
}
