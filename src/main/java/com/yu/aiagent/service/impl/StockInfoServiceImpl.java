package com.yu.aiagent.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yu.aiagent.exception.BusinessException;
import com.yu.aiagent.exception.ErrorCode;
import com.yu.aiagent.mapper.StockInfoMapper;
import com.yu.aiagent.model.dto.stock.StockInfoAddRequest;
import com.yu.aiagent.model.dto.stock.StockInfoQueryRequest;
import com.yu.aiagent.model.dto.stock.StockInfoUpdateRequest;
import com.yu.aiagent.model.entity.StockInfo;
import com.yu.aiagent.service.StockInfoService;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 股票池管理服务实现。
 *
 * <p>股票池是 AI 股票大师进行结构化检索的重要数据来源，管理员在后台维护股票代码、
 * 行业、主题标签、风险标签、入池理由和热度分后，后续 AI 分析可以基于这些结构化字段
 * 更稳定地召回候选股票，而不是完全依赖自然语言或手写 SQL。</p>
 */
@Service
public class StockInfoServiceImpl extends ServiceImpl<StockInfoMapper, StockInfo>
        implements StockInfoService {

    /**
     * 允许前端排序的字段白名单。
     *
     * <p>排序字段会拼到 SQL 中，必须使用白名单限制，避免用户传入任意字段造成 SQL 注入风险。</p>
     */
    private static final Set<String> ALLOW_SORT_FIELDS = Set.of(
            "id", "stockCode", "stockName", "market", "industry", "hotScore", "createTime", "updateTime"
    );

    /**
     * 新增股票池记录。
     *
     * <p>新增时股票代码和股票名称是必填项，其他字段可以逐步补充；保存前统一调用
     * {@link #validateStockInfo(StockInfo, boolean)} 做长度和热度分校验。</p>
     */
    @Override
    public Long addStockInfo(StockInfoAddRequest addRequest) {
        if (addRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 使用 BeanUtil 做 DTO 到实体的字段拷贝，避免手动 set 漏字段。
        StockInfo stockInfo = new StockInfo();
        BeanUtil.copyProperties(addRequest, stockInfo);
        // 新增模式下会强制校验股票代码和股票名称不能为空。
        validateStockInfo(stockInfo, false);
        // 明确设置逻辑删除标记，保证新数据默认可见。
        stockInfo.setIsDelete(0);
        boolean saved = this.save(stockInfo);
        if (!saved) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        return stockInfo.getId();
    }

    /**
     * 更新股票池记录。
     *
     * <p>更新前先查旧数据，确保记录存在；更新模式允许局部字段为空，
     * 但只要传入了字段，就会做长度和取值范围校验。</p>
     */
    @Override
    public Boolean updateStockInfo(StockInfoUpdateRequest updateRequest) {
        if (updateRequest == null || updateRequest.getId() == null || updateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 先查一次旧数据，给前端明确的“记录不存在”错误，而不是静默 update 0 行。
        StockInfo oldStockInfo = this.getById(updateRequest.getId());
        if (oldStockInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        StockInfo stockInfo = new StockInfo();
        BeanUtil.copyProperties(updateRequest, stockInfo);
        // 更新模式不强制代码和名称必填，便于后台做局部编辑。
        validateStockInfo(stockInfo, true);
        boolean updated = this.updateById(stockInfo);
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        return true;
    }

    /**
     * 构造股票池分页查询条件。
     *
     * <p>支持按代码精确查询，按名称、行业、主题、风险和理由模糊查询。
     * 没有合法排序字段时，默认按热度分和更新时间倒序，让更重要、更新的数据优先展示。</p>
     */
    @Override
    public QueryWrapper<StockInfo> getQueryWrapper(StockInfoQueryRequest queryRequest) {
        StockInfoQueryRequest request = queryRequest == null ? new StockInfoQueryRequest() : queryRequest;
        QueryWrapper<StockInfo> queryWrapper = new QueryWrapper<>();
        // 股票代码通常是确定值，使用等值匹配；名称、行业、标签类字段更适合模糊查询。
        queryWrapper.eq(StrUtil.isNotBlank(request.getStockCode()), "stockCode", request.getStockCode());
        queryWrapper.like(StrUtil.isNotBlank(request.getStockName()), "stockName", request.getStockName());
        queryWrapper.eq(StrUtil.isNotBlank(request.getMarket()), "market", request.getMarket());
        queryWrapper.like(StrUtil.isNotBlank(request.getIndustry()), "industry", request.getIndustry());
        queryWrapper.like(StrUtil.isNotBlank(request.getThemeKeyword()), "themes", request.getThemeKeyword());
        queryWrapper.like(StrUtil.isNotBlank(request.getRiskKeyword()), "riskTags", request.getRiskKeyword());
        queryWrapper.like(StrUtil.isNotBlank(request.getReasonKeyword()), "reason", request.getReasonKeyword());

        String sortField = request.getSortField();
        String sortOrder = request.getSortOrder();
        if (StrUtil.isNotBlank(sortField) && ALLOW_SORT_FIELDS.contains(sortField)) {
            // 只有白名单字段才允许排序，sortOrder 使用前端 Ant Design 的 ascend 表示升序。
            queryWrapper.orderBy(true, "ascend".equals(sortOrder), sortField);
        } else {
            // 后台默认视角：热度高、最近维护过的股票优先出现。
            queryWrapper.orderByDesc("hotScore").orderByDesc("updateTime");
        }
        return queryWrapper;
    }

    /**
     * 管理后台保存前统一校验，避免无效股票池数据进入 AI 检索上下文。
     *
     * @param stockInfo  待校验的股票池实体
     * @param updateMode 是否为更新模式；新增模式会额外要求股票代码和名称必填
     */
    private void validateStockInfo(StockInfo stockInfo, boolean updateMode) {
        if (!updateMode && (StrUtil.isBlank(stockInfo.getStockCode()) || StrUtil.isBlank(stockInfo.getStockName()))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "股票代码和股票名称不能为空");
        }
        // 字符串长度要和数据库字段长度保持一致，提前在业务层返回更友好的错误。
        validateLength(stockInfo.getStockCode(), 32, "股票代码过长");
        validateLength(stockInfo.getStockName(), 64, "股票名称过长");
        validateLength(stockInfo.getMarket(), 32, "市场过长");
        validateLength(stockInfo.getIndustry(), 128, "行业过长");
        validateLength(stockInfo.getThemes(), 512, "主题标签过长");
        validateLength(stockInfo.getRiskTags(), 512, "风险标签过长");
        if (stockInfo.getHotScore() != null && (stockInfo.getHotScore() < 0 || stockInfo.getHotScore() > 1000)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "热度分需在 0 到 1000 之间");
        }
    }

    /**
     * 校验字符串最大长度。
     *
     * <p>null 表示本次没有传入该字段，不做校验；非空字符串超长时抛出业务异常。</p>
     */
    private void validateLength(String value, int maxLength, String message) {
        if (value != null && value.length() > maxLength) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, message);
        }
    }
}
