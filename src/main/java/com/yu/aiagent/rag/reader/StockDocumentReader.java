package com.yu.aiagent.rag.reader;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 股票知识库文档读取器统一接口。
 *
 * <p>这个接口是 DocumentReader 扩展的核心抽象。
 * 不同来源的数据格式可能完全不同：Markdown 是文件文本，CSV 是结构化表格，
 * PDF 是公告文档，网页是 HTML。但只要最终都转换成 Spring AI 的 Document，
 * 后续向量化、检索、RAG 增强就可以复用同一套流程。</p>
 */
public interface StockDocumentReader {

    /**
     * 读取当前数据源中的股票知识文档。
     *
     * @return 可加入向量库的文档列表；没有数据时返回空列表
     */
    List<Document> read();
}
