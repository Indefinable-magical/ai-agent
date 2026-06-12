package com.yu.aiagent.rag.reader;

import cn.hutool.core.text.csv.CsvData;
import cn.hutool.core.text.csv.CsvReader;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV 股票池读取器。
 *
 * <p>读取 src/main/resources/document/stock-pool/*.csv。
 * 适合维护结构化股票池，比如股票代码、股票名称、行业、题材、风险标签、关注理由等。
 * 程序启动时会把每一行股票数据转换成一个 Document，加入向量库。</p>
 */
@Component
@Order(20)
@Slf4j
public class CsvStockPoolDocumentReader implements StockDocumentReader {

    /**
     * CSV 股票池所在路径。
     */
    private static final String CSV_LOCATION_PATTERN = "classpath:document/stock-pool/*.csv";

    private final ResourcePatternResolver resourcePatternResolver;

    public CsvStockPoolDocumentReader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 读取所有 CSV 股票池文件。
     */
    @Override
    public List<Document> read() {
        List<Document> documents = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources(CSV_LOCATION_PATTERN);
            for (Resource resource : resources) {
                documents.addAll(readResource(resource));
            }
        } catch (Exception e) {
            log.error("CSV 股票池加载失败", e);
        }
        return documents;
    }

    /**
     * 读取单个 CSV 文件。
     *
     * <p>约定第一行是表头，从第二行开始才是真正的股票数据。</p>
     */
    private List<Document> readResource(Resource resource) throws Exception {
        List<Document> documents = new ArrayList<>();
        String filename = resource.getFilename();

        // Hutool CsvReader 负责解析 CSV，避免自己用字符串 split 处理逗号和转义。
        CsvReader reader = CsvUtil.getReader();
        CsvData csvData = reader.read(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
        if (csvData.getRowCount() <= 1) {
            return documents;
        }

        // 第一行作为表头，用来把每一列映射成字段名。
        List<String> headers = csvData.getRow(0).getRawList();
        for (int i = 1; i < csvData.getRowCount(); i++) {
            CsvRow row = csvData.getRow(i);
            Map<String, String> values = toValueMap(headers, row);
            String stockName = values.getOrDefault("stockName", "");
            String stockCode = values.getOrDefault("stockCode", "");

            // 股票名称和代码都为空，说明这一行不是有效股票数据，直接跳过。
            if (StrUtil.isBlank(stockName) && StrUtil.isBlank(stockCode)) {
                continue;
            }

            // 把结构化字段组织成自然语言文本，便于向量模型理解。
            String text = """
                    股票：%s（%s）
                    行业：%s
                    主题：%s
                    风险标签：%s
                    关注理由：%s
                    适合问题：%s
                    """.formatted(
                    stockName,
                    stockCode,
                    values.getOrDefault("industry", ""),
                    values.getOrDefault("themes", ""),
                    values.getOrDefault("riskTags", ""),
                    values.getOrDefault("reason", ""),
                    values.getOrDefault("questions", "")
            );

            // metadata 是后续检索、分组、风险过滤、来源展示的关键。
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("filename", filename);
            metadata.put("sourceType", "csv_stock_pool");
            metadata.put("sourceName", filename);
            metadata.put("stockCode", stockCode);
            metadata.put("stockName", stockName);
            metadata.put("industry", values.getOrDefault("industry", ""));
            metadata.put("themes", values.getOrDefault("themes", ""));
            metadata.put("riskTags", values.getOrDefault("riskTags", ""));
            metadata.put("riskLevel", detectRiskLevel(values.getOrDefault("riskTags", "")));

            documents.add(new Document(text, metadata));
        }
        return documents;
    }

    /**
     * 把 CSV 行转换成字段名到字段值的 Map。
     */
    private Map<String, String> toValueMap(List<String> headers, CsvRow row) {
        Map<String, String> valueMap = new LinkedHashMap<>();
        for (int i = 0; i < headers.size() && i < row.size(); i++) {
            valueMap.put(headers.get(i), row.get(i));
        }
        return valueMap;
    }

    /**
     * 根据风险标签给股票池数据打风险等级。
     */
    private String detectRiskLevel(String riskTags) {
        if (StrUtil.containsAnyIgnoreCase(riskTags, "妖股", "高波动", "连板", "退潮", "监管")) {
            return "high";
        }
        if (StrUtil.containsAnyIgnoreCase(riskTags, "估值", "周期", "业绩")) {
            return "medium";
        }
        return "low";
    }
}
