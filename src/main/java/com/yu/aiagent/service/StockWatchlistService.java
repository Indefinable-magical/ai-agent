package com.yu.aiagent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yu.aiagent.model.dto.stock.StockWatchlistAddRequest;
import com.yu.aiagent.model.dto.stock.StockWatchlistUpdateRequest;
import com.yu.aiagent.model.entity.StockWatchlist;
import com.yu.aiagent.model.vo.StockWatchlistVO;

import java.util.List;

public interface StockWatchlistService extends IService<StockWatchlist> {

    /**
     * 查询当前用户的自选股列表。
     */
    List<StockWatchlistVO> listMyWatchlist(Long userId);

    /**
     * 添加当前用户的自选股。
     */
    Long addMyWatchlist(Long userId, StockWatchlistAddRequest request);

    /**
     * 更新当前用户的自选股。
     */
    Boolean updateMyWatchlist(Long userId, StockWatchlistUpdateRequest request);

    /**
     * 删除当前用户的自选股。
     */
    Boolean deleteMyWatchlist(Long userId, Long id);

    /**
     * 构建用于注入 AI 股票大师的自选股上下文。
     */
    String buildWatchlistContext(Long userId);
}
