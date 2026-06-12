package com.yu.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 对话记忆
 * @TableName chat_memory
 */
@TableName(value ="chat_memory")
@Data
public class ChatMemory {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话 id
     */
    private String conversationId;

    /**
     * 用户 id，未登录或系统会话可为空
     */
    private Long userId;

    /**
     * 应用类型：stock_app/manus 等
     */
    private String appType;

    /**
     * 消息类型：USER/ASSISTANT/SYSTEM/TOOL
     */
    private String messageType;

    /**
     * 消息正文
     */
    private String content;

    /**
     * 消息元数据，保存模型、工具调用等扩展信息
     */
    private Object metadata;

    /**
     * 消息在会话中的顺序
     */
    private Integer messageOrder;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;
}
