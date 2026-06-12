package com.yu.aiagent.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * AI 超级智能体会话摘要视图。
 */
@Data
public class ManusConversationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话 id，前端用它加载、继续和删除指定会话。
     */
    private String conversationId;

    /**
     * 会话标题，默认取第一条用户消息生成。
     */
    private String title;

    /**
     * 最近更新时间，用于前端排序和展示。
     */
    private Date updateTime;
}
