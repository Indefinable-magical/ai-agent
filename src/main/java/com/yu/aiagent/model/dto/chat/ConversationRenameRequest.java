package com.yu.aiagent.model.dto.chat;

import lombok.Data;

import java.io.Serializable;

/**
 * 会话重命名请求
 */
@Data
public class ConversationRenameRequest implements Serializable {

    /**
     * 会话名称
     */
    private String title;

    private static final long serialVersionUID = 1L;
}
