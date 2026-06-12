package com.yu.aiagent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yu.aiagent.model.dto.user.UserPreferenceUpdateRequest;
import com.yu.aiagent.model.entity.UserPreference;
import com.yu.aiagent.model.vo.UserPreferenceVO;

/**
 * 用户通用偏好服务。
 */
public interface UserPreferenceService extends IService<UserPreference> {

    /**
     * 获取用户偏好，没有配置时返回默认配置。
     */
    UserPreferenceVO getMyPreference(Long userId);

    /**
     * 更新用户偏好。
     */
    UserPreferenceVO updateMyPreference(Long userId, UserPreferenceUpdateRequest preferenceUpdateRequest);

    /**
     * 仅同步风险偏好，供股票大师风险偏好入口复用。
     */
    void updateRiskPreference(Long userId, String riskPreference);
}
