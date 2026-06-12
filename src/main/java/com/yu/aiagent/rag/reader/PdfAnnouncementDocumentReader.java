package com.yu.aiagent.rag.reader;

import cn.hutool.core.util.StrUtil;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 股票公告 PDF 读取器。
 *
 * <p>读取 src/main/resources/document/announcements/*.pdf。
 * 适合放公司财报、交易所公告、重大事项公告等文件。
 * 读取后会提取 PDF 文本，并作为 RAG 文档加入向量库。</p>
 */
@Component
@Order(30)
@Slf4j
public class PdfAnnouncementDocumentReader implements StockDocumentReader {

    /**
     * 单个 PDF 最多读取的字符数。
     *
     * <p>公告 PDF 可能很长，全部塞进向量库会增加启动耗时和 token 噪声，
     * 所以这里先限制长度。后续如果要更精细，可以按页或按章节切分。</p>
     */
    private static final int MAX_TEXT_LENGTH = 12000;

    private final ResourcePatternResolver resourcePatternResolver;

    public PdfAnnouncementDocumentReader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    @Override
    public List<Document> read() {
        List<Document> documents = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/announcements/*.pdf");
            for (Resource resource : resources) {
                Document document = readPdf(resource);
                if (document != null) {
                    documents.add(document);
                }
            }
        } catch (Exception e) {
            log.error("PDF 股票公告加载失败", e);
        }
        return documents;
    }

    /**
     * 读取单个 PDF，并转换成 Document。
     */
    private Document readPdf(Resource resource) {
        String filename = resource.getFilename();
        try (PdfDocument pdfDocument = new PdfDocument(new PdfReader(resource.getInputStream()))) {
            StringBuilder contentBuilder = new StringBuilder();

            // iText 的页码从 1 开始，不是从 0 开始。
            for (int page = 1; page <= pdfDocument.getNumberOfPages(); page++) {
                contentBuilder.append(PdfTextExtractor.getTextFromPage(pdfDocument.getPage(page))).append('\n');
                if (contentBuilder.length() >= MAX_TEXT_LENGTH) {
                    break;
                }
            }

            String content = StrUtil.subPre(contentBuilder.toString().trim(), MAX_TEXT_LENGTH);
            if (StrUtil.isBlank(content)) {
                return null;
            }

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("filename", filename);
            metadata.put("sourceType", "pdf_announcement");
            metadata.put("sourceName", filename);
            metadata.put("riskLevel", "medium");
            return new Document("股票公告 PDF：" + filename + "\n" + content, metadata);
        } catch (Exception e) {
            log.warn("读取股票公告 PDF 失败：{}", filename, e);
            return null;
        }
    }
}
