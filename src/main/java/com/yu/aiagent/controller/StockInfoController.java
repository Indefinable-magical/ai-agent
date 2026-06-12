package com.yu.aiagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yu.aiagent.annotation.AuthCheck;
import com.yu.aiagent.common.BaseResponse;
import com.yu.aiagent.common.DeleteRequest;
import com.yu.aiagent.common.ResultUtils;
import com.yu.aiagent.constant.UserConstant;
import com.yu.aiagent.exception.BusinessException;
import com.yu.aiagent.exception.ErrorCode;
import com.yu.aiagent.exception.ThrowUtils;
import com.yu.aiagent.model.dto.stock.StockInfoAddRequest;
import com.yu.aiagent.model.dto.stock.StockInfoQueryRequest;
import com.yu.aiagent.model.dto.stock.StockInfoUpdateRequest;
import com.yu.aiagent.model.entity.StockInfo;
import com.yu.aiagent.service.StockInfoService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 股票池管理接口。
 */
@RestController
@RequestMapping("/admin/stock-pool")
public class StockInfoController {

    @Resource
    private StockInfoService stockInfoService;

    /**
     * 新增股票池记录。
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addStockInfo(@RequestBody StockInfoAddRequest addRequest) {
        return ResultUtils.success(stockInfoService.addStockInfo(addRequest));
    }

    /**
     * 更新股票池记录。
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateStockInfo(@RequestBody StockInfoUpdateRequest updateRequest) {
        return ResultUtils.success(stockInfoService.updateStockInfo(updateRequest));
    }

    /**
     * 删除股票池记录。
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteStockInfo(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean removed = stockInfoService.removeById(deleteRequest.getId());
        ThrowUtils.throwIf(!removed, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 分页查询股票池。
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<StockInfo>> listStockInfoByPage(@RequestBody StockInfoQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<StockInfo> page = stockInfoService.page(Page.of(queryRequest.getPageNum(), queryRequest.getPageSize()),
                stockInfoService.getQueryWrapper(queryRequest));
        return ResultUtils.success(page);
    }
}
