package com.yu.aiagent.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户偏好更新请求。
 */
@Data
public class UserPreferenceUpdateRequest implements Serializable {

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

    private static final long serialVersionUID = 1L;
}
