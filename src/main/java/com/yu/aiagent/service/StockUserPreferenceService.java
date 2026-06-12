package com.yu.aiagent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yu.aiagent.model.entity.StockUserPreference;
import com.yu.aiagent.model.vo.StockUserPreferenceVO;

public interface StockUserPreferenceService extends IService<StockUserPreference> {

    /**
     * 获取当前用户股票大师偏好。
     */
    StockUserPreferenceVO getMyPreference(Long userId);

    /**
     * 更新当前用户风险偏好。
     */
    StockUserPreferenceVO updateRiskPreference(Long userId, String riskPreference);

    /**
     * 构建用于注入 AI 股票大师的风险偏好上下文。
     */
    String buildRiskPreferenceContext(Long userId);
}
