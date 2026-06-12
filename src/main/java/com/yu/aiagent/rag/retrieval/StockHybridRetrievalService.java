package com.yu.aiagent.rag.retrieval;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.yu.aiagent.rag.transformer.StockQueryIntent;
import com.yu.aiagent.rag.transformer.StockQueryTransformResult;
import com.yu.aiagent.rag.transformer.StockQueryTransformer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 股票混合检索服务。
 *
 * <p>这是自定义 RAG 的核心检索层。它把不同来源的结果统一成 StockRagDocument，
 * 再做风险过滤、去重、排序、分组和 Prompt 上下文拼接。</p>
 *
 * <p>当前接入的检索来源：</p>
 * <p>1. VectorStore：负责本地 Markdown、CSV、PDF、网页等文档的语义检索。</p>
 * <p>2. MySQL：负责 stock_info 表中的结构化股票数据检索。</p>
 * <p>3. Redis：负责热门问题、热门股票缓存。</p>
 *
 * <p>你已经明确不使用 Elasticsearch，所以这里不包含 ES 相关代码。</p>
 */
@Service
@Slf4j
public class StockHybridRetrievalService {

    /**
     * 最多注入给大模型的知识条数。
     *
     * <p>RAG 不是召回越多越好，太多内容会稀释重点、增加 token 消耗，
     * 所以这里控制最多 8 条。</p>
     */
    private static final int MAX_CONTEXT_DOCUMENTS = 8;

    /**
     * 向量库默认权重。
     */
    private static final double VECTOR_WEIGHT = 1.0;

    /**
     * Spring AI 向量库，保存由 DocumentReader 加载进来的文档。
     */
    private final VectorStore stockAppVectorStore;

    /**
     * 查询转换器，负责问题改写、关键词扩展、意图识别。
     */
    private final StockQueryTransformer stockQueryTransformer;

    /**
     * MySQL 结构化检索服务。
     */
    private final StockStructuredSearchService stockStructuredSearchService;

    /**
     * Redis 热门问题和热门股票缓存服务。
     */
    private final StockHotCacheService stockHotCacheService;

    public StockHybridRetrievalService(VectorStore stockAppVectorStore,
                                       StockQueryTransformer stockQueryTransformer,
                                       StockStructuredSearchService stockStructuredSearchService,
                                       StockHotCacheService stockHotCacheService) {
        this.stockAppVectorStore = stockAppVectorStore;
        this.stockQueryTransformer = stockQueryTransformer;
        this.stockStructuredSearchService = stockStructuredSearchService;
        this.stockHotCacheService = stockHotCacheService;
    }

    /**
     * 执行完整混合检索。
     *
     * <p>完整流程：</p>
     * <p>1. 先把用户原始问题转换成更适合检索的问题。</p>
     * <p>2. 把原始问题记录到 Redis 热门问题中。</p>
     * <p>3. 分别从向量库、MySQL、Redis 召回候选资料。</p>
     * <p>4. 过滤掉不适合当前问题的高风险资料。</p>
     * <p>5. 去掉重复资料。</p>
     * <p>6. 按来源权重排序，并限制最多注入条数。</p>
     * <p>7. 把资料拼接成给大模型使用的 RAG 上下文。</p>
     */
    public StockRagContext retrieve(String userQuery) {
        // 第一步：把“算力有什么票”这类口语问题，改写成更适合检索的专业表达。
        StockQueryTransformResult transformResult = stockQueryTransformer.transform(userQuery);

        // 第二步：把用户问题计入 Redis 热门问题，后续可用于热点提示和运营分析。
        stockHotCacheService.recordQuery(transformResult.getOriginalQuery());

        // 第三步：聚合三类来源的候选资料。
        List<StockRagDocument> documents = new ArrayList<>();
        documents.addAll(searchVectorStore(transformResult));
        documents.addAll(stockStructuredSearchService.search(
                transformResult.getOriginalQuery(), transformResult.getExpandedKeywords()));
        documents.addAll(stockHotCacheService.searchHotItems());

        // 第四步：过滤、去重、按权重排序、限制数量。
        List<StockRagDocument> filteredDocuments = documents.stream()
                .filter(document -> isAllowedByRisk(transformResult, document))
                .filter(document -> StrUtil.isNotBlank(document.getContent()))
                .collect(LinkedHashMap<String, StockRagDocument>::new,
                        (map, document) -> map.putIfAbsent(uniqueKey(document), document),
                        LinkedHashMap::putAll)
                .values()
                .stream()
                .sorted(Comparator.comparingDouble(StockRagDocument::getWeight).reversed())
                .limit(MAX_CONTEXT_DOCUMENTS)
                .toList();

        // 第五步：把命中的股票名称写入 Redis 热门股票缓存。
        recordHotStocks(filteredDocuments);

        // 第六步：把检索结果拼成 Prompt 文本。
        String contextText = buildContextText(transformResult, filteredDocuments);

        return StockRagContext.builder()
                .query(transformResult)
                .documents(filteredDocuments)
                .hasContext(CollUtil.isNotEmpty(filteredDocuments))
                .contextText(contextText)
                .build();
    }

    /**
     * 从向量库中做语义检索。
     */
    private List<StockRagDocument> searchVectorStore(StockQueryTransformResult transformResult) {
        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(transformResult.getRewrittenQuery())
                    .topK(8)
                    .similarityThreshold(0.35)
                    .build();
            return stockAppVectorStore.similaritySearch(searchRequest).stream()
                    .map(this::toRagDocument)
                    .toList();
        } catch (Exception e) {
            // 向量库异常不能影响主流程，直接降级为空结果。
            log.warn("向量知识库检索失败，已降级为空结果：{}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 把 Spring AI 的 Document 转成项目内部统一的 StockRagDocument。
     */
    private StockRagDocument toRagDocument(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        String sourceType = Objects.toString(metadata.getOrDefault("sourceType", "vector"), "vector");
        String sourceName = Objects.toString(metadata.getOrDefault("sourceName",
                metadata.getOrDefault("filename", "本地知识库")), "本地知识库");
        String riskLevel = Objects.toString(metadata.getOrDefault("riskLevel", "medium"), "medium");
        return StockRagDocument.builder()
                .sourceType(sourceType)
                .sourceName(sourceName)
                .groupName(inferGroupName(document.getText(), metadata))
                .content(document.getText())
                .weight(resolveSourceWeight(sourceType))
                .riskLevel(riskLevel)
                .build();
    }

    /**
     * 给不同来源设置权重。
     *
     * <p>CSV 股票池通常是人工整理的结构化股票池，优先级最高；
     * PDF 公告比较正式，优先级次之；网页研报可能噪声更多，权重略低。</p>
     */
    private double resolveSourceWeight(String sourceType) {
        return switch (sourceType) {
            case "csv_stock_pool" -> 1.3;
            case "pdf_announcement" -> 1.1;
            case "web_research" -> 0.9;
            case "markdown" -> 1.0;
            default -> VECTOR_WEIGHT;
        };
    }

    /**
     * 根据内容粗略推断资料分组。
     *
     * <p>分组会影响最终 Prompt 的结构，让模型按“股票 / 行业 / 主题 / 风险”
     * 来组织答案，而不是把所有资料混成一团。</p>
     */
    private String inferGroupName(String text, Map<String, Object> metadata) {
        String stockName = Objects.toString(metadata.getOrDefault("stockName", ""), "");
        if (StrUtil.isNotBlank(stockName) || StrUtil.containsAny(text, "股票：", "标的", "代码")) {
            return "股票";
        }
        if (StrUtil.containsAny(text, "行业", "产业链", "板块", "赛道")) {
            return "行业";
        }
        if (StrUtil.containsAny(text, "风险", "回撤", "退潮", "监管")) {
            return "风险";
        }
        return "主题";
    }

    /**
     * 根据当前问题决定是否允许高风险资料进入上下文。
     *
     * <p>如果资料被标记为 high，但用户并没有问妖股、高波动、短线、风险，
     * 就不主动把这类内容注入给大模型，避免模型过度输出高风险方向。</p>
     */
    private boolean isAllowedByRisk(StockQueryTransformResult transformResult, StockRagDocument document) {
        if (!"high".equalsIgnoreCase(document.getRiskLevel())) {
            return true;
        }
        return transformResult.getIntent() == StockQueryIntent.RISK_ANALYSIS
                || StrUtil.containsAny(transformResult.getOriginalQuery(), "妖股", "高波动", "连板", "短线");
    }

    /**
     * 把命中的股票写入 Redis 热门股票缓存。
     */
    private void recordHotStocks(List<StockRagDocument> documents) {
        for (StockRagDocument document : documents) {
            String content = document.getContent();
            if (StrUtil.startWith(content, "股票：")) {
                String stockName = StrUtil.subBetween(content, "股票：", "（");
                stockHotCacheService.recordStock(stockName);
            }
        }
    }

    /**
     * 生成去重 key。
     */
    private String uniqueKey(StockRagDocument document) {
        return document.getSourceType() + ":" + document.getSourceName() + ":" + StrUtil.subPre(document.getContent(), 80);
    }

    /**
     * 把检索结果拼接成给大模型看的上下文。
     */
    private String buildContextText(StockQueryTransformResult transformResult, List<StockRagDocument> documents) {
        if (CollUtil.isEmpty(documents)) {
            return """
                    【股票知识库检索结果】
                    未检索到足够相关的股票知识。

                    回答要求：
                    1. 不要编造不存在的知识库内容。
                    2. 先说明“当前知识库未命中足够相关资料”。
                    3. 引导用户补充股票代码、行业、主题或风险偏好。
                    4. 内容仅供学习研究，不构成投资建议。
                    """;
        }

        Map<String, List<StockRagDocument>> groupMap = new LinkedHashMap<>();
        for (StockRagDocument document : documents) {
            groupMap.computeIfAbsent(document.getGroupName(), key -> new ArrayList<>()).add(document);
        }

        StringBuilder builder = new StringBuilder();
        builder.append("【股票知识库检索结果】\n");
        builder.append("原始问题：").append(transformResult.getOriginalQuery()).append('\n');
        builder.append("改写问题：").append(transformResult.getRewrittenQuery()).append('\n');
        builder.append("识别意图：").append(transformResult.getIntent()).append("\n\n");

        for (Map.Entry<String, List<StockRagDocument>> entry : groupMap.entrySet()) {
            builder.append("【").append(entry.getKey()).append("】\n");
            int index = 1;
            for (StockRagDocument document : entry.getValue()) {
                builder.append(index++).append(". ")
                        .append(removeSourceTraces(document.getContent())).append('\n')
                        .append("风险等级：").append(document.getRiskLevel())
                        .append("\n\n");
            }
        }

        builder.append("回答要求：\n");
        builder.append("1. 优先使用上面的知识库内容，不要脱离资料强行荐股。\n");
        builder.append("2. 按股票 / 行业 / 主题 / 风险组织答案。\n");
        builder.append("3. 高波动或高风险内容必须明确提示风险。\n");
        builder.append("4. 不要向用户暴露文件名、表名、数据库名、RAG、CSV、Markdown、PDF、stock_info 等内部实现细节。\n");
        builder.append("5. 不要输出“引用来源”“参考来源”“来源”“知识来源”等章节；只把可靠结论融入正文。\n");
        builder.append("6. 内容仅供学习研究，不构成投资建议。\n");
        return builder.toString();
    }

    private String removeSourceTraces(String content) {
        if (StrUtil.isBlank(content)) {
            return "";
        }
        return content.lines()
                .filter(line -> {
                    String trimmed = line.trim();
                    return !trimmed.startsWith("来源：")
                            && !trimmed.startsWith("来源:")
                            && !trimmed.startsWith("参考来源：")
                            && !trimmed.startsWith("参考来源:")
                            && !trimmed.startsWith("引用来源：")
                            && !trimmed.startsWith("引用来源:")
                            && !trimmed.startsWith("知识来源：")
                            && !trimmed.startsWith("知识来源:");
                })
                .map(line -> line
                        .replace("stock_info", "结构化股票资料")
                        .replace("csv_stock_pool", "股票池资料")
                        .replace("pdf_announcement", "公告资料")
                        .replace("web_research", "公开资料"))
                .collect(Collectors.joining("\n"));
    }
}
