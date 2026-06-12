package com.yu.aiagent.rag;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 股票大师向量数据库配置。
 *
 * <p>启动时会把本地知识库文档加载进内存向量库 SimpleVectorStore。
 * 注意：DashScope embedding 对单条输入长度有限制，所以入库前必须先过滤空文档、
 * 再切分长文档，避免启动时报 input length 超过范围。</p>
 */
@Configuration
public class StockAppVectorStoreConfig {

    /**
     * 多来源文档加载器：Markdown、CSV、PDF、网页。
     */
    @Resource
    private StockAppDocumentLoader stockAppDocumentLoader;

    /**
     * Token 文本切分器，用来把长文档拆成更小的片段。
     */
    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    /**
     * 关键词元信息增强器，会为文档补充 excerpt_keywords 等元数据。
     */
    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    @Bean
    VectorStore stockAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();

        // 1. 加载 Markdown、CSV、PDF、网页等多来源文档。
        List<Document> documentList = stockAppDocumentLoader.loadMarkdowns();

        // 2. 先过滤空文档。DashScope embedding 要求输入长度至少为 1。
        List<Document> nonBlankDocuments = filterBlankDocuments(documentList);

        // 3. 再做 Token 切分。DashScope embedding 要求单条输入不要超过 2048。
        List<Document> splitDocuments = myTokenTextSplitter.splitCustomized(nonBlankDocuments);

        // 4. 兜底做一次字符长度切分，防止个别文档按 token 切分后仍然过长。
        List<Document> safeDocuments = splitLongDocuments(splitDocuments);

        // 5. 给切分后的短文档补充关键词元信息。
        List<Document> enrichedDocuments = myKeywordEnricher.enrichDocuments(safeDocuments);

        // 6. 写入内存向量库。后续 StockAppRagAdvisor 会从这里做语义检索。
        simpleVectorStore.add(enrichedDocuments);
        return simpleVectorStore;
    }

    /**
     * 过滤空文本 Document。
     */
    private List<Document> filterBlankDocuments(List<Document> documents) {
        return documents.stream()
                .filter(document -> document != null && StrUtil.isNotBlank(document.getText()))
                .toList();
    }

    /**
     * 按字符长度做兜底切分。
     *
     * <p>这里用 1600 字符作为保守上限，给 metadata、格式化内容和模型 tokenizer 留出余量。</p>
     */
    private List<Document> splitLongDocuments(List<Document> documents) {
        List<Document> result = new ArrayList<>();
        for (Document document : documents) {
            String text = document.getText();
            if (StrUtil.isBlank(text)) {
                continue;
            }
            if (text.length() <= 1600) {
                result.add(document);
                continue;
            }
            for (int start = 0; start < text.length(); start += 1600) {
                int end = Math.min(start + 1600, text.length());
                result.add(new Document(text.substring(start, end), document.getMetadata()));
            }
        }
        return result;
    }
}
