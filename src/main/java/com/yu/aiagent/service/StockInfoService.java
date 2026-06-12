package com.yu.aiagent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yu.aiagent.model.dto.stock.StockInfoAddRequest;
import com.yu.aiagent.model.dto.stock.StockInfoQueryRequest;
import com.yu.aiagent.model.dto.stock.StockInfoUpdateRequest;
import com.yu.aiagent.model.entity.StockInfo;

/**
 * 股票池管理服务。
 */
public interface StockInfoService extends IService<StockInfo> {

    /**
     * 新增股票池记录。
     */
    Long addStockInfo(StockInfoAddRequest addRequest);

    /**
     * 更新股票池记录。
     */
    Boolean updateStockInfo(StockInfoUpdateRequest updateRequest);

    /**
     * 构造股票池查询条件。
     */
    QueryWrapper<StockInfo> getQueryWrapper(StockInfoQueryRequest queryRequest);
}
