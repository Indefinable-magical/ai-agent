package com.yu.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 用户通用偏好设置。
 *
 * <p>这张表保存跨设备生效的体验配置，比如界面主题、默认 AI 应用、默认风险偏好和对话密度。</p>
 */
@TableName(value = "user_preference")
@Data
public class UserPreference {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户 id。
     */
    private Long userId;

    /**
     * 界面主题：cyber / light。
     */
    private String theme;

    /**
     * 默认 AI 应用：home / stock-master / super-agent。
     */
    private String defaultAiApp;

    /**
     * 默认风险偏好：conservative / balanced / aggressive。
     */
    private String defaultRiskPreference;

    /**
     * 对话展示密度：compact / comfortable。
     */
    private String conversationDensity;

    /**
     * 创建时间。
     */
    private Date createTime;

    /**
     * 更新时间。
     */
    private Date updateTime;

    /**
     * 逻辑删除标记。
     */
    @TableLogic
    private Integer isDelete;
}
