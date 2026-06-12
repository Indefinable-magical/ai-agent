package com.yu.aiagent.model.vo;

import lombok.Data;

/**
 * 股票大师用户偏好视图。
 */
@Data
public class StockUserPreferenceVO {

    /**
     * 风险偏好编码
     */
    private String riskPreference;

    /**
     * 风险偏好中文名
     */
    private String riskPreferenceName;
}
