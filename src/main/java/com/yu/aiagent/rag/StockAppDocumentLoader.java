package com.yu.aiagent.rag;

import com.yu.aiagent.rag.reader.StockDocumentReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 股票大师应用文档加载器。
 *
 * <p>这是所有知识库数据进入向量库前的统一入口。
 * 它不关心具体数据来自 Markdown、CSV、PDF 还是网页，
 * 只负责调度所有 StockDocumentReader，并把它们读取到的 Document 合并起来。</p>
 */
@Component
@Slf4j
public class StockAppDocumentLoader {

    /**
     * Spring 会自动注入所有实现了 StockDocumentReader 的 Bean。
     *
     * <p>目前包括：</p>
     * <p>1. MarkdownStockDocumentReader：读取本地 Markdown。</p>
     * <p>2. CsvStockPoolDocumentReader：读取 CSV 股票池。</p>
     * <p>3. PdfAnnouncementDocumentReader：读取股票公告 PDF。</p>
     * <p>4. WebResearchDocumentReader：读取网页研报摘要。</p>
     */
    private final List<StockDocumentReader> stockDocumentReaders;

    public StockAppDocumentLoader(List<StockDocumentReader> stockDocumentReaders) {
        this.stockDocumentReaders = stockDocumentReaders;
    }

    /**
     * 加载股票应用的全部知识文档。
     *
     * <p>方法名保留为 loadMarkdowns，是为了兼容项目里原来的调用点；
     * 但现在它实际加载的是多来源文档，不再只是 Markdown。</p>
     */
    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>();
        for (StockDocumentReader stockDocumentReader : stockDocumentReaders) {
            try {
                // 每个 Reader 只负责自己的数据源，职责清晰，后续新增数据源也不用改这里。
                List<Document> documents = stockDocumentReader.read();
                allDocuments.addAll(documents);
                log.info("股票知识库读取器 {} 加载文档 {} 条",
                        stockDocumentReader.getClass().getSimpleName(), documents.size());
            } catch (Exception e) {
                // 单个数据源失败不应该让整个知识库启动失败，所以这里捕获异常继续加载其他 Reader。
                log.error("股票知识库读取器 {} 执行失败",
                        stockDocumentReader.getClass().getSimpleName(), e);
            }
        }
        return allDocuments;
    }
}
