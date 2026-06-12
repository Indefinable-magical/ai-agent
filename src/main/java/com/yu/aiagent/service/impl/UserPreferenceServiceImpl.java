package com.yu.aiagent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yu.aiagent.exception.BusinessException;
import com.yu.aiagent.exception.ErrorCode;
import com.yu.aiagent.mapper.StockUserPreferenceMapper;
import com.yu.aiagent.mapper.UserPreferenceMapper;
import com.yu.aiagent.model.dto.user.UserPreferenceUpdateRequest;
import com.yu.aiagent.model.entity.StockUserPreference;
import com.yu.aiagent.model.entity.UserPreference;
import com.yu.aiagent.model.vo.UserPreferenceVO;
import com.yu.aiagent.service.UserPreferenceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 用户通用偏好服务实现。
 *
 * <p>通用偏好用于保存跨页面、跨设备都应该一致的体验设置，例如界面主题、
 * 默认进入的 AI 应用、默认风险偏好和对话展示密度。前端登录后会读取这份配置并应用到界面。</p>
 */
@Service
public class UserPreferenceServiceImpl extends ServiceImpl<UserPreferenceMapper, UserPreference>
        implements UserPreferenceService {

    /**
     * 默认界面主题：赛博风格。
     */
    public static final String DEFAULT_THEME = "cyber";

    /**
     * 默认 AI 应用：首页。
     */
    public static final String DEFAULT_AI_APP = "home";

    /**
     * 默认风险偏好：平衡。
     */
    public static final String DEFAULT_RISK_PREFERENCE = "balanced";

    /**
     * 默认对话展示密度：舒适。
     */
    public static final String DEFAULT_CONVERSATION_DENSITY = "comfortable";

    /**
     * 合法主题集合，所有写入数据库的主题值都必须在这里。
     */
    private static final Set<String> VALID_THEMES = Set.of("cyber", "light");

    /**
     * 合法默认应用集合，避免前端传入任意路由造成跳转异常。
     */
    private static final Set<String> VALID_DEFAULT_AI_APPS = Set.of("home", "stock-master", "super-agent");

    /**
     * 合法风险偏好集合，和股票大师专用偏好保持一致。
     */
    private static final Set<String> VALID_RISK_PREFERENCES = Set.of("conservative", "balanced", "aggressive");

    /**
     * 合法对话密度集合。
     */
    private static final Set<String> VALID_CONVERSATION_DENSITIES = Set.of("compact", "comfortable");

    /**
     * 风险偏好中文名称映射，返回 VO 时直接带给前端展示。
     */
    private static final Map<String, String> RISK_PREFERENCE_NAME_MAP = Map.of(
            "conservative", "稳健",
            "balanced", "平衡",
            "aggressive", "激进"
    );

    @Resource
    private StockUserPreferenceMapper stockUserPreferenceMapper;

    /**
     * 获取当前用户通用偏好。
     *
     * <p>如果用户首次访问还没有偏好记录，会自动创建一条默认配置。</p>
     */
    @Override
    public UserPreferenceVO getMyPreference(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return toVO(getOrCreatePreference(userId));
    }

    /**
     * 更新当前用户通用偏好。
     *
     * <p>每个字段都允许为空：为空时沿用原值；非空时必须落在对应合法集合里。
     * 更新默认风险偏好后，会同步到股票大师专用风险偏好表。</p>
     */
    @Override
    public UserPreferenceVO updateMyPreference(Long userId, UserPreferenceUpdateRequest preferenceUpdateRequest) {
        if (userId == null || preferenceUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserPreference preference = getOrCreatePreference(userId);
        // 对每个偏好字段做“空值沿用原值、非法值报错”的统一处理。
        preference.setTheme(normalizeRequestValue(preferenceUpdateRequest.getTheme(), preference.getTheme(),
                VALID_THEMES, "主题不合法"));
        preference.setDefaultAiApp(normalizeRequestValue(preferenceUpdateRequest.getDefaultAiApp(),
                preference.getDefaultAiApp(), VALID_DEFAULT_AI_APPS, "默认 AI 应用不合法"));
        preference.setDefaultRiskPreference(normalizeRequestValue(preferenceUpdateRequest.getDefaultRiskPreference(),
                preference.getDefaultRiskPreference(), VALID_RISK_PREFERENCES, "风险偏好不合法"));
        preference.setConversationDensity(normalizeRequestValue(preferenceUpdateRequest.getConversationDensity(),
                preference.getConversationDensity(), VALID_CONVERSATION_DENSITIES, "对话密度不合法"));

        boolean updated = this.updateById(preference);
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        syncStockRiskPreference(userId, preference.getDefaultRiskPreference());
        return toVO(preference);
    }

    /**
     * 只更新通用偏好中的风险偏好。
     *
     * <p>股票大师页面修改风险偏好时会调用这个方法，把专用偏好同步到通用偏好。</p>
     */
    @Override
    public void updateRiskPreference(Long userId, String riskPreference) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String normalizedRiskPreference = normalizeRequestValue(riskPreference, DEFAULT_RISK_PREFERENCE,
                VALID_RISK_PREFERENCES, "风险偏好不合法");
        UserPreference preference = getOrCreatePreference(userId);
        preference.setDefaultRiskPreference(normalizedRiskPreference);
        boolean updated = this.updateById(preference);
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
    }

    /**
     * 查询用户偏好；首次使用时创建默认偏好，保证前端总能拿到完整配置。
     */
    private UserPreference getOrCreatePreference(Long userId) {
        LambdaQueryWrapper<UserPreference> queryWrapper = new LambdaQueryWrapper<UserPreference>()
                .eq(UserPreference::getUserId, userId)
                .eq(UserPreference::getIsDelete, 0);
        UserPreference preference = this.getOne(queryWrapper, false);
        if (preference != null) {
            // 已存在的老记录可能字段为空或非法，读出时先补齐默认值再返回。
            fillDefaultValues(preference);
            return preference;
        }

        // 首次使用时创建默认偏好，避免前端每次都做大量 null 判断。
        UserPreference defaultPreference = new UserPreference();
        defaultPreference.setUserId(userId);
        defaultPreference.setTheme(DEFAULT_THEME);
        defaultPreference.setDefaultAiApp(DEFAULT_AI_APP);
        defaultPreference.setDefaultRiskPreference(DEFAULT_RISK_PREFERENCE);
        defaultPreference.setConversationDensity(DEFAULT_CONVERSATION_DENSITY);
        defaultPreference.setIsDelete(0);
        boolean saved = this.save(defaultPreference);
        if (!saved) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        return defaultPreference;
    }

    /**
     * 老数据可能缺少部分字段，这里统一补默认值，避免前端做大量兜底判断。
     */
    private void fillDefaultValues(UserPreference preference) {
        preference.setTheme(normalizeStoredValue(preference.getTheme(), DEFAULT_THEME, VALID_THEMES));
        preference.setDefaultAiApp(normalizeStoredValue(preference.getDefaultAiApp(),
                DEFAULT_AI_APP, VALID_DEFAULT_AI_APPS));
        preference.setDefaultRiskPreference(normalizeStoredValue(preference.getDefaultRiskPreference(),
                DEFAULT_RISK_PREFERENCE, VALID_RISK_PREFERENCES));
        preference.setConversationDensity(normalizeStoredValue(preference.getConversationDensity(),
                DEFAULT_CONVERSATION_DENSITY, VALID_CONVERSATION_DENSITIES));
    }

    /**
     * 规范化请求值。
     *
     * <p>请求值为空时使用默认值；请求值不在合法集合中时直接报错，防止非法配置写入数据库。</p>
     */
    private String normalizeRequestValue(String value, String defaultValue, Set<String> validValues, String errorMessage) {
        String normalizedValue = StrUtil.blankToDefault(value, defaultValue).trim();
        if (!validValues.contains(normalizedValue)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, errorMessage);
        }
        return normalizedValue;
    }

    /**
     * 规范化数据库已存值。
     *
     * <p>历史数据如果存了非法值，这里不会抛错，而是回退默认值，保证用户仍能正常进入系统。</p>
     */
    private String normalizeStoredValue(String value, String defaultValue, Set<String> validValues) {
        String normalizedValue = StrUtil.blankToDefault(value, defaultValue).trim();
        if (!validValues.contains(normalizedValue)) {
            return defaultValue;
        }
        return normalizedValue;
    }

    /**
     * 通用偏好里的风险偏好变更后，同步股票大师专用偏好表，保证两个入口展示一致。
     */
    private void syncStockRiskPreference(Long userId, String riskPreference) {
        LambdaQueryWrapper<StockUserPreference> queryWrapper = new LambdaQueryWrapper<StockUserPreference>()
                .eq(StockUserPreference::getUserId, userId)
                .eq(StockUserPreference::getIsDelete, 0);
        StockUserPreference stockPreference = stockUserPreferenceMapper.selectOne(queryWrapper);
        if (stockPreference == null) {
            // 用户从资料页首次设置默认风险偏好时，自动补齐股票大师专用偏好。
            StockUserPreference newPreference = new StockUserPreference();
            newPreference.setUserId(userId);
            newPreference.setRiskPreference(riskPreference);
            newPreference.setIsDelete(0);
            stockUserPreferenceMapper.insert(newPreference);
            return;
        }

        // 已有股票大师偏好时只更新风险偏好字段，不改其他数据。
        LambdaUpdateWrapper<StockUserPreference> updateWrapper = new LambdaUpdateWrapper<StockUserPreference>()
                .set(StockUserPreference::getRiskPreference, riskPreference)
                .eq(StockUserPreference::getId, stockPreference.getId())
                .eq(StockUserPreference::getUserId, userId);
        stockUserPreferenceMapper.update(updateWrapper);
    }

    /**
     * 转换为前端展示对象。
     */
    private UserPreferenceVO toVO(UserPreference preference) {
        UserPreferenceVO vo = new UserPreferenceVO();
        vo.setTheme(preference.getTheme());
        vo.setDefaultAiApp(preference.getDefaultAiApp());
        vo.setDefaultRiskPreference(preference.getDefaultRiskPreference());
        vo.setDefaultRiskPreferenceName(RISK_PREFERENCE_NAME_MAP.getOrDefault(preference.getDefaultRiskPreference(), "平衡"));
        vo.setConversationDensity(preference.getConversationDensity());
        return vo;
    }
}
