package com.yu.aiagent.rag.reader;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 股票知识读取器。
 *
 * <p>负责读取 src/main/resources/document 目录下的 *.md 文件。
 * 这是项目原本已有的本地知识库形式，现在被抽象成一个 StockDocumentReader，
 * 方便和 CSV、PDF、网页等新数据源一起被 StockAppDocumentLoader 统一调度。</p>
 */
@Component
@Order(10)
@Slf4j
public class MarkdownStockDocumentReader implements StockDocumentReader {

    /**
     * Spring 的资源解析器，支持 classpath 通配符扫描。
     */
    private final ResourcePatternResolver resourcePatternResolver;

    public MarkdownStockDocumentReader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 读取所有 Markdown 文档并转换成 Spring AI Document。
     */
    @Override
    public List<Document> read() {
        List<Document> documents = new ArrayList<>();
        try {
            // 扫描 resources/document 下的全部 Markdown 文件。
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) {
                    continue;
                }

                // MarkdownDocumentReaderConfig 用来控制 Markdown 如何切分成 Document。
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", filename)
                        .withAdditionalMetadata("sourceType", "markdown")
                        .withAdditionalMetadata("sourceName", filename)
                        .withAdditionalMetadata("riskLevel", detectRiskLevel(filename))
                        .build();

                // Spring AI 官方 Markdown Reader 会把 Markdown 内容解析成 Document 列表。
                documents.addAll(new MarkdownDocumentReader(resource, config).get());
            }
        } catch (IOException e) {
            log.error("Markdown 股票知识文档加载失败", e);
        }
        return documents;
    }

    /**
     * 根据文件名粗略标记风险等级。
     *
     * <p>风险等级会在 StockHybridRetrievalService 里用于过滤高风险内容。</p>
     */
    private String detectRiskLevel(String filename) {
        if (filename.contains("风险") || filename.contains("关注对象")) {
            return "medium";
        }
        return "low";
    }
}
