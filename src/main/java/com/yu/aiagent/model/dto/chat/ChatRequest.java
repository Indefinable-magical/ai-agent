package com.yu.aiagent.model.dto.chat;

import lombok.Data;

import java.io.Serializable;

/**
 * 聊天请求。
 */
@Data
public class ChatRequest implements Serializable {

    /**
     * 用户消息。
     */
    private String message;

    /**
     * 会话 id。
     */
    private String chatId;

    private static final long serialVersionUID = 1L;
}
