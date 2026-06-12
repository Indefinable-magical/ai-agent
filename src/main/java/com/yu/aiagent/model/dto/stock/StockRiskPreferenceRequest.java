package com.yu.aiagent.model.dto.stock;

import lombok.Data;

import java.io.Serializable;

/**
 * 股票分析风险偏好请求
 */
@Data
public class StockRiskPreferenceRequest implements Serializable {

    /**
     * 风险偏好：conservative / balanced / aggressive
     */
    private String riskPreference;

    private static final long serialVersionUID = 1L;
}
