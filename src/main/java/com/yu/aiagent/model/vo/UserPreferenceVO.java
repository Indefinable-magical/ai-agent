package com.yu.aiagent.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户通用偏好展示对象。
 */
@Data
public class UserPreferenceVO implements Serializable {

    /**
     * 界面主题。
     */
    private String theme;

    /**
     * 默认 AI 应用。
     */
    private String defaultAiApp;

    /**
     * 默认风险偏好。
     */
    private String defaultRiskPreference;

    /**
     * 默认风险偏好中文名。
     */
    private String defaultRiskPreferenceName;

    /**
     * 对话展示密度。
     */
    private String conversationDensity;

    private static final long serialVersionUID = 1L;
}
