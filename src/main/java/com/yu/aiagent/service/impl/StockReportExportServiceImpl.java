package com.yu.aiagent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.yu.aiagent.exception.BusinessException;
import com.yu.aiagent.exception.ErrorCode;
import com.yu.aiagent.service.StockReportExportService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 股票分析报告导出服务。
 *
 * <p>当前前端先把会话内容整理成 Markdown，再交给这里生成 PDF。
 * 这样 Markdown 和 PDF 共用同一份报告内容，后续如果要增强 PDF 样式，
 * 只需要继续扩展本服务的渲染逻辑。</p>
 */
@Service
public class StockReportExportServiceImpl implements StockReportExportService {

    /**
     * Windows 常见中文字体候选，优先用系统字体，避免中文 PDF 乱码。
     */
    private static final List<String> FONT_CANDIDATES = List.of(
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/simsun.ttc,0",
            "C:/Windows/Fonts/msyh.ttc,0",
            "C:/Windows/Fonts/STSONG.TTF"
    );

    @Override
    public byte[] generatePdfFromMarkdown(String markdown) {
        if (StrUtil.isBlank(markdown)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "报告内容不能为空");
        }
        // 使用内存输出流生成 PDF 字节，Controller 可以直接作为文件流返回给浏览器下载。
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(outputStream);
             PdfDocument pdfDocument = new PdfDocument(writer);
             Document document = new Document(pdfDocument)) {
            // 设置统一页边距，避免中文段落贴边影响阅读。
            document.setMargins(36, 36, 36, 36);
            // 必须设置中文字体，否则 iText 默认字体容易出现中文缺字或乱码。
            document.setFont(createChineseFont());
            // 先清洗不可打印字符，再按轻量 Markdown 规则写入 PDF。
            addMarkdownContent(document, normalizeContent(markdown));
            document.close();
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "PDF 报告生成失败");
        }
    }

    /**
     * 创建中文 PDF 字体。
     *
     * <p>优先扫描 Windows 常见中文字体；如果都不存在，再使用 iText 内置的中文字体兜底。
     * 这里不把字体文件放进项目，是为了避免仓库体积变大和字体授权问题。</p>
     */
    private PdfFont createChineseFont() throws IOException {
        for (String fontPath : FONT_CANDIDATES) {
            String actualPath = fontPath.split(",", 2)[0];
            if (Files.exists(Path.of(actualPath))) {
                // IDENTITY_H 支持 Unicode 中文编码，嵌入字体可以提升不同电脑打开 PDF 的一致性。
                return PdfFontFactory.createFont(
                        fontPath,
                        PdfEncodings.IDENTITY_H,
                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
                );
            }
        }
        return PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
    }

    /**
     * 轻量 Markdown 渲染：保留标题、列表和正文层级，复杂表格按文本排版。
     *
     * <p>这不是完整 Markdown 引擎，只覆盖当前报告最常见的结构：
     * 标题、分隔线、列表、引用和普通段落。这样实现简单，也便于后续替换成更完整的渲染方案。</p>
     */
    private void addMarkdownContent(Document document, String content) {
        for (String line : content.split("\\R")) {
            String text = line.trim();
            if (text.isEmpty()) {
                continue;
            }
            if ("---".equals(text)) {
                // Markdown 分隔线在 PDF 中先转成留白，避免生成过重的横线影响版面。
                document.add(new Paragraph(" ").setMarginBottom(4));
                continue;
            }

            // stripMarkdown 会把 Markdown 标记去掉，保留用户真正要阅读的文字。
            Paragraph paragraph = new Paragraph(stripMarkdown(text))
                    .setMultipliedLeading(1.25f)
                    .setMarginBottom(6);

            // 根据 Markdown 前缀设置不同字号和间距，让导出的 PDF 层级更清晰。
            if (text.startsWith("# ")) {
                paragraph.setFontSize(18).setMarginTop(8);
            } else if (text.startsWith("## ")) {
                paragraph.setFontSize(15).setMarginTop(8);
            } else if (text.startsWith("### ")) {
                paragraph.setFontSize(13).setMarginTop(6);
            } else if (text.startsWith("- ") || text.startsWith("* ")) {
                paragraph.setFirstLineIndent(12).setFontSize(11);
            } else if (text.startsWith("> ")) {
                paragraph.setFontSize(10);
            } else {
                paragraph.setFontSize(11);
            }

            document.add(paragraph);
        }
    }

    /**
     * 清洗 PDF 不容易处理的字符。
     *
     * <p>部分 AI 输出可能包含代理区字符或不可见控制字符，直接写入 PDF 可能导致字体渲染失败。
     * 这里保留常见换行、回车和制表符，其余控制字符会被过滤。</p>
     */
    private String normalizeContent(String content) {
        StringBuilder result = new StringBuilder();
        content.codePoints()
                .filter(codePoint -> codePoint <= Character.MAX_VALUE)
                .filter(codePoint -> !Character.isISOControl(codePoint)
                        || codePoint == '\n'
                        || codePoint == '\r'
                        || codePoint == '\t')
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    /**
     * 去除轻量 Markdown 标记，转成适合直接写入 PDF 段落的文本。
     */
    private String stripMarkdown(String text) {
        return text
                .replaceFirst("^#{1,6}\\s*", "")
                .replaceFirst("^[-*]\\s*", "• ")
                .replaceFirst("^>\\s*", "")
                .replace("**", "")
                .replace("`", "")
                .replaceAll("\\[(.+?)]\\((.+?)\\)", "$1 ($2)");
    }
}
