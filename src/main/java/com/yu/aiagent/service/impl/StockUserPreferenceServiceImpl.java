package com.yu.aiagent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yu.aiagent.exception.BusinessException;
import com.yu.aiagent.exception.ErrorCode;
import com.yu.aiagent.mapper.StockUserPreferenceMapper;
import com.yu.aiagent.model.entity.StockUserPreference;
import com.yu.aiagent.model.vo.StockUserPreferenceVO;
import com.yu.aiagent.service.StockUserPreferenceService;
import com.yu.aiagent.service.UserPreferenceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 股票大师用户偏好服务实现。
 *
 * <p>这里保存的是股票大师专用的风险偏好。它会和通用用户偏好中的默认风险偏好保持同步，
 * 这样用户无论是在资料页还是股票大师页面修改风险偏好，最终 AI 分析使用的配置都一致。</p>
 */
@Service
public class StockUserPreferenceServiceImpl extends ServiceImpl<StockUserPreferenceMapper, StockUserPreference>
        implements StockUserPreferenceService {

    @Resource
    private UserPreferenceService userPreferenceService;

    /**
     * 默认风险偏好：平衡。
     */
    public static final String DEFAULT_RISK_PREFERENCE = "balanced";

    /**
     * 风险偏好编码到中文名称的映射，前端展示和 AI 上下文都可以复用。
     */
    private static final Map<String, String> RISK_PREFERENCE_NAME_MAP = Map.of(
            "conservative", "稳健",
            "balanced", "平衡",
            "aggressive", "激进"
    );

    /**
     * 查询当前用户的股票风险偏好。
     *
     * <p>返回 VO 时会同时包含偏好编码和中文名称，前端可以直接展示，不需要再维护一份映射。</p>
     */
    @Override
    public StockUserPreferenceVO getMyPreference(Long userId) {
        String riskPreference = getRiskPreference(userId);
        return toVO(riskPreference);
    }

    /**
     * 保存或更新用户风险偏好。
     *
     * <p>如果用户还没配置过，就插入一条新记录；如果已经有记录，就直接更新。</p>
     */
    @Override
    public StockUserPreferenceVO updateRiskPreference(Long userId, String riskPreference) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String normalizedPreference = normalizeRiskPreference(riskPreference);
        StockUserPreference oldPreference = getByUserId(userId);
        if (oldPreference == null) {
            // 第一次设置股票风险偏好时创建新记录，并显式标记为未删除。
            StockUserPreference preference = new StockUserPreference();
            preference.setUserId(userId);
            preference.setRiskPreference(normalizedPreference);
            preference.setIsDelete(0);
            boolean saved = this.save(preference);
            if (!saved) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR);
            }
            // 股票大师专用偏好更新后，同步通用偏好，保证资料页展示一致。
            userPreferenceService.updateRiskPreference(userId, normalizedPreference);
            return toVO(normalizedPreference);
        }

        // 已有记录时只更新风险偏好字段，避免误改其他审计字段。
        LambdaUpdateWrapper<StockUserPreference> updateWrapper = new LambdaUpdateWrapper<StockUserPreference>()
                .set(StockUserPreference::getRiskPreference, normalizedPreference)
                .eq(StockUserPreference::getId, oldPreference.getId())
                .eq(StockUserPreference::getUserId, userId);
        boolean updated = this.update(updateWrapper);
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        // 股票大师风险偏好也是用户默认风险偏好，更新时同步到通用偏好表。
        userPreferenceService.updateRiskPreference(userId, normalizedPreference);
        return toVO(normalizedPreference);
    }

    /**
     * 拼接给 AI 使用的风险偏好上下文。
     *
     * <p>这段文本会参与提示词，让 AI 在分析股票时自动调整风险提示强度和表达方式。
     * 这里明确要求“不要承诺收益”，避免投资建议语气过强。</p>
     */
    @Override
    public String buildRiskPreferenceContext(Long userId) {
        String riskPreference = getRiskPreference(userId);
        String riskPreferenceName = RISK_PREFERENCE_NAME_MAP.getOrDefault(riskPreference, "平衡");
        return "用户风险偏好：" + riskPreferenceName + "。"
                + "分析时请据此调整风险提示、仓位建议和语言风格；不要承诺收益。";
    }

    /**
     * 获取用户当前风险偏好，没有配置时返回默认值。
     */
    private String getRiskPreference(Long userId) {
        StockUserPreference preference = getByUserId(userId);
        if (preference == null || StrUtil.isBlank(preference.getRiskPreference())) {
            // 没有股票大师专用配置时，回退到用户通用偏好的默认风险偏好。
            return userPreferenceService.getMyPreference(userId).getDefaultRiskPreference();
        }
        return normalizeRiskPreference(preference.getRiskPreference());
    }

    /**
     * 按用户查询偏好记录。
     */
    private StockUserPreference getByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        // 只查询未逻辑删除的记录；getOne(..., false) 避免多条历史脏数据直接抛异常。
        LambdaQueryWrapper<StockUserPreference> queryWrapper = new LambdaQueryWrapper<StockUserPreference>()
                .eq(StockUserPreference::getUserId, userId)
                .eq(StockUserPreference::getIsDelete, 0);
        return this.getOne(queryWrapper, false);
    }

    /**
     * 统一收口风险偏好取值，避免出现非法编码。
     */
    private String normalizeRiskPreference(String riskPreference) {
        String normalizedPreference = StrUtil.blankToDefault(riskPreference, DEFAULT_RISK_PREFERENCE).trim();
        if (!RISK_PREFERENCE_NAME_MAP.containsKey(normalizedPreference)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "风险偏好不合法");
        }
        return normalizedPreference;
    }

    /**
     * 把偏好编码转换成前端可直接展示的数据。
     */
    private StockUserPreferenceVO toVO(String riskPreference) {
        StockUserPreferenceVO vo = new StockUserPreferenceVO();
        vo.setRiskPreference(riskPreference);
        vo.setRiskPreferenceName(RISK_PREFERENCE_NAME_MAP.getOrDefault(riskPreference, "平衡"));
        return vo;
    }
}
