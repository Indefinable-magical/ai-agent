package com.yu.aiagent.service;

public interface StockReportExportService {

    /**
     * 根据 Markdown 内容生成 PDF 字节。
     *
     * @param markdown Markdown 报告内容
     * @return PDF 字节数组
     */
    byte[] generatePdfFromMarkdown(String markdown);
}
