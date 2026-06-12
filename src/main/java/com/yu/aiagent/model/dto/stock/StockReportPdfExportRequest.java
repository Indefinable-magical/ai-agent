package com.yu.aiagent.model.dto.stock;

import lombok.Data;

import java.io.Serializable;

/**
 * 股票分析报告 PDF 导出请求
 */
@Data
public class StockReportPdfExportRequest implements Serializable {

    /**
     * PDF 文件名
     */
    private String fileName;

    /**
     * Markdown 报告内容
     */
    private String markdown;

    private static final long serialVersionUID = 1L;
}
