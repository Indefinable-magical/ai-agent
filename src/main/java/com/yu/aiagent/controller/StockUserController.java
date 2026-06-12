package com.yu.aiagent.controller;

import com.yu.aiagent.annotation.AuthCheck;
import com.yu.aiagent.common.BaseResponse;
import com.yu.aiagent.common.DeleteRequest;
import com.yu.aiagent.common.ResultUtils;
import com.yu.aiagent.exception.ErrorCode;
import com.yu.aiagent.exception.ThrowUtils;
import com.yu.aiagent.model.dto.stock.StockReportPdfExportRequest;
import com.yu.aiagent.model.dto.stock.StockRiskPreferenceRequest;
import com.yu.aiagent.model.dto.stock.StockWatchlistAddRequest;
import com.yu.aiagent.model.dto.stock.StockWatchlistUpdateRequest;
import com.yu.aiagent.model.entity.User;
import com.yu.aiagent.model.vo.StockUserPreferenceVO;
import com.yu.aiagent.model.vo.StockWatchlistVO;
import com.yu.aiagent.service.StockReportExportService;
import com.yu.aiagent.service.StockUserPreferenceService;
import com.yu.aiagent.service.StockWatchlistService;
import com.yu.aiagent.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 股票大师用户个性化配置接口
 */
@RestController
@RequestMapping("/stock/user")
public class StockUserController {

    @Resource
    private UserService userService;

    @Resource
    private StockWatchlistService stockWatchlistService;

    @Resource
    private StockUserPreferenceService stockUserPreferenceService;

    @Resource
    private StockReportExportService stockReportExportService;

    /**
     * 查询当前登录用户的自选股。
     */
    @AuthCheck
    @GetMapping("/watchlist")
    public BaseResponse<List<StockWatchlistVO>> listMyWatchlist(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(stockWatchlistService.listMyWatchlist(loginUser.getId()));
    }

    /**
     * 添加当前登录用户的自选股。
     */
    @AuthCheck
    @PostMapping("/watchlist/add")
    public BaseResponse<Long> addMyWatchlist(@RequestBody StockWatchlistAddRequest addRequest,
                                             HttpServletRequest request) {
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(stockWatchlistService.addMyWatchlist(loginUser.getId(), addRequest));
    }

    /**
     * 更新当前登录用户的自选股。
     */
    @AuthCheck
    @PostMapping("/watchlist/update")
    public BaseResponse<Boolean> updateMyWatchlist(@RequestBody StockWatchlistUpdateRequest updateRequest,
                                                   HttpServletRequest request) {
        ThrowUtils.throwIf(updateRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(stockWatchlistService.updateMyWatchlist(loginUser.getId(), updateRequest));
    }

    /**
     * 删除当前登录用户的自选股。
     */
    @AuthCheck
    @PostMapping("/watchlist/delete")
    public BaseResponse<Boolean> deleteMyWatchlist(@RequestBody DeleteRequest deleteRequest,
                                                   HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(stockWatchlistService.deleteMyWatchlist(loginUser.getId(), deleteRequest.getId()));
    }

    /**
     * 获取当前登录用户的股票大师偏好。
     */
    @AuthCheck
    @GetMapping("/preference")
    public BaseResponse<StockUserPreferenceVO> getMyPreference(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(stockUserPreferenceService.getMyPreference(loginUser.getId()));
    }

    /**
     * 更新当前登录用户的风险偏好。
     */
    @AuthCheck
    @PostMapping("/preference/risk")
    public BaseResponse<StockUserPreferenceVO> updateRiskPreference(@RequestBody StockRiskPreferenceRequest riskRequest,
                                                                    HttpServletRequest request) {
        ThrowUtils.throwIf(riskRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(stockUserPreferenceService.updateRiskPreference(
                loginUser.getId(), riskRequest.getRiskPreference()));
    }

    /**
     * 将前端整理好的 Markdown 股票分析报告导出为 PDF。
     */
    @AuthCheck
    @PostMapping(value = "/report/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportReportPdf(@RequestBody StockReportPdfExportRequest exportRequest,
                                                  HttpServletRequest request) {
        ThrowUtils.throwIf(exportRequest == null, ErrorCode.PARAMS_ERROR);
        userService.getLoginUser(request);
        byte[] pdfBytes = stockReportExportService.generatePdfFromMarkdown(exportRequest.getMarkdown());
        String fileName = normalizePdfFileName(exportRequest.getFileName());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(pdfBytes);
    }

    private String normalizePdfFileName(String fileName) {
        String normalizedFileName = fileName == null || fileName.isBlank()
                ? "股票分析报告.pdf"
                : fileName.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
        return normalizedFileName.toLowerCase().endsWith(".pdf") ? normalizedFileName : normalizedFileName + ".pdf";
    }
}
