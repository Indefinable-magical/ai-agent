package com.yu.aiagent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yu.aiagent.exception.BusinessException;
import com.yu.aiagent.exception.ErrorCode;
import com.yu.aiagent.mapper.StockWatchlistMapper;
import com.yu.aiagent.model.dto.stock.StockWatchlistAddRequest;
import com.yu.aiagent.model.dto.stock.StockWatchlistUpdateRequest;
import com.yu.aiagent.model.entity.StockWatchlist;
import com.yu.aiagent.model.vo.StockWatchlistVO;
import com.yu.aiagent.service.StockWatchlistService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 用户自选股服务实现。
 *
 * <p>自选股是 AI 股票大师的用户侧上下文。用户没有明确指定股票时，
 * AI 可以优先围绕这里维护的股票进行分析，从而减少每次对话重复输入关注标的。</p>
 */
@Service
public class StockWatchlistServiceImpl extends ServiceImpl<StockWatchlistMapper, StockWatchlist>
        implements StockWatchlistService {

    /**
     * 自选股数量上限，防止用户上下文过长影响分析质量。
     */
    private static final int MAX_WATCHLIST_SIZE = 30;

    /**
     * 查询当前用户自选股列表。
     *
     * <p>服务层只返回当前用户自己的记录，避免前端传参越权查看其他用户的自选股。</p>
     */
    @Override
    public List<StockWatchlistVO> listMyWatchlist(Long userId) {
        return listByUserId(userId).stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 添加当前用户自选股。
     *
     * <p>新增前会校验输入、数量上限和同一用户下股票代码是否重复。
     * 股票代码和名称做 trim 后保存，避免用户无意输入空格导致重复判断失效。</p>
     */
    @Override
    public Long addMyWatchlist(Long userId, StockWatchlistAddRequest request) {
        if (userId == null || request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 基础字段校验集中在服务层，Controller 保持薄层转发。
        validateStockInput(request.getStockCode(), request.getStockName(), request.getRemark());
        if (listByUserId(userId).size() >= MAX_WATCHLIST_SIZE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "自选股最多添加 30 只");
        }
        // 同一个用户同一只股票只能维护一条自选记录。
        StockWatchlist oldItem = getByUserIdAndCode(userId, request.getStockCode());
        if (oldItem != null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该股票已在自选股中");
        }

        // 保存时明确写入用户 id，不能相信前端传入用户归属字段。
        StockWatchlist item = new StockWatchlist();
        item.setUserId(userId);
        item.setStockCode(StrUtil.trim(request.getStockCode()));
        item.setStockName(StrUtil.trim(request.getStockName()));
        item.setRemark(StrUtil.trim(request.getRemark()));
        item.setIsDelete(0);
        boolean saved = this.save(item);
        if (!saved) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        return item.getId();
    }

    /**
     * 更新自选股时，先校验归属关系，再避免用户把同一只股票重复维护成两条记录。
     */
    @Override
    public Boolean updateMyWatchlist(Long userId, StockWatchlistUpdateRequest request) {
        if (userId == null || request == null || request.getId() == null || request.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        validateStockInput(request.getStockCode(), request.getStockName(), request.getRemark());
        // 先根据 id 查出原记录，再校验 userId，防止用户更新其他人的自选股。
        StockWatchlist oldItem = this.getById(request.getId());
        if (oldItem == null || !Objects.equals(oldItem.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 如果修改了股票代码，需要确认不会和该用户的其他自选股冲突。
        StockWatchlist sameCodeItem = getByUserIdAndCode(userId, request.getStockCode());
        if (sameCodeItem != null && !Objects.equals(sameCodeItem.getId(), request.getId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该股票已在自选股中");
        }

        // 只更新允许用户编辑的业务字段，用户 id、删除标记等字段不从请求中读取。
        StockWatchlist updateItem = new StockWatchlist();
        updateItem.setId(request.getId());
        updateItem.setStockCode(StrUtil.trim(request.getStockCode()));
        updateItem.setStockName(StrUtil.trim(request.getStockName()));
        updateItem.setRemark(StrUtil.trim(request.getRemark()));
        return this.updateById(updateItem);
    }

    /**
     * 软删除自选股，保留历史记录，避免后续排查用户配置变更时没有依据。
     */
    @Override
    public Boolean deleteMyWatchlist(Long userId, Long id) {
        if (userId == null || id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 删除时同时带上 userId 条件，保证只能删除自己的自选股。
        LambdaQueryWrapper<StockWatchlist> queryWrapper = new LambdaQueryWrapper<StockWatchlist>()
                .eq(StockWatchlist::getId, id)
                .eq(StockWatchlist::getUserId, userId);
        return this.remove(queryWrapper);
    }

    /**
     * 把自选股整理成一段可直接喂给 AI 的上下文。
     */
    @Override
    public String buildWatchlistContext(Long userId) {
        List<StockWatchlist> watchlist = listByUserId(userId);
        if (watchlist.isEmpty()) {
            return "用户暂未维护自选股。";
        }
        // 按自然语言拼接成提示词片段，方便 StockApp 在调用模型前直接附加到上下文。
        StringBuilder context = new StringBuilder("用户自选股：");
        for (StockWatchlist item : watchlist) {
            context.append("\n- ")
                    .append(item.getStockCode())
                    .append(" ")
                    .append(item.getStockName());
            if (StrUtil.isNotBlank(item.getRemark())) {
                context.append("，关注点：").append(item.getRemark());
            }
        }
        context.append("\n当用户问题没有明确股票标的时，优先围绕上述自选股给出分析。");
        return context.toString();
    }

    /**
     * 按用户查询自选股列表。
     *
     * <p>按照更新时间和 id 倒序展示，最近维护的股票更靠前，也更符合用户当前关注。</p>
     */
    private List<StockWatchlist> listByUserId(Long userId) {
        LambdaQueryWrapper<StockWatchlist> queryWrapper = new LambdaQueryWrapper<StockWatchlist>()
                .eq(StockWatchlist::getUserId, userId)
                .eq(StockWatchlist::getIsDelete, 0)
                .orderByDesc(StockWatchlist::getUpdateTime)
                .orderByDesc(StockWatchlist::getId);
        return this.list(queryWrapper);
    }

    /**
     * 根据用户和股票代码查重。
     */
    private StockWatchlist getByUserIdAndCode(Long userId, String stockCode) {
        if (StrUtil.isBlank(stockCode)) {
            return null;
        }
        // 查重时同样只看未删除记录，用户删除后可以重新添加同一只股票。
        LambdaQueryWrapper<StockWatchlist> queryWrapper = new LambdaQueryWrapper<StockWatchlist>()
                .eq(StockWatchlist::getUserId, userId)
                .eq(StockWatchlist::getStockCode, StrUtil.trim(stockCode))
                .eq(StockWatchlist::getIsDelete, 0);
        return this.getOne(queryWrapper, false);
    }

    /**
     * 统一校验股票代码、名称和备注长度，避免 Controller 层散落规则。
     */
    private void validateStockInput(String stockCode, String stockName, String remark) {
        if (StrUtil.hasBlank(stockCode, stockName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请填写股票代码和名称");
        }
        // 长度限制和数据库字段保持一致，提前拦截可以返回更明确的业务提示。
        if (stockCode.length() > 32 || stockName.length() > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "股票代码或名称过长");
        }
        if (remark != null && remark.length() > 256) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "关注理由不能超过 256 个字符");
        }
    }

    /**
     * 把实体转换成前端展示需要的 VO。
     */
    private StockWatchlistVO toVO(StockWatchlist item) {
        StockWatchlistVO vo = new StockWatchlistVO();
        vo.setId(item.getId());
        vo.setStockCode(item.getStockCode());
        vo.setStockName(item.getStockName());
        vo.setRemark(item.getRemark());
        vo.setCreateTime(item.getCreateTime());
        return vo;
    }
}
