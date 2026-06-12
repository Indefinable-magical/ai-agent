package com.yu.aiagent.rag.retrieval;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yu.aiagent.mapper.StockInfoMapper;
import com.yu.aiagent.model.entity.StockInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * MySQL 结构化股票检索服务。
 *
 * <p>向量库适合做语义检索，但不擅长精确查股票代码、行业字段、主题标签。
 * 所以这里用 MySQL 的 stock_info 表保存结构化股票数据，作为 RAG 的补充来源。</p>
 */
@Service
@Slf4j
public class StockStructuredSearchService {

    /**
     * MySQL 最多返回的股票记录数。
     */
    private static final int MYSQL_LIMIT = 8;

    private final StockInfoMapper stockInfoMapper;

    public StockStructuredSearchService(StockInfoMapper stockInfoMapper) {
        this.stockInfoMapper = stockInfoMapper;
    }

    /**
     * 根据用户问题和扩展关键词检索 stock_info 表。
     *
     * <p>如果用户暂时还没创建 stock_info 表，或者数据库不可用，
     * 这里会捕获异常并返回空列表，不影响向量库 RAG 正常工作。</p>
     */
    public List<StockRagDocument> search(String query, List<String> keywords) {
        try {
            LambdaQueryWrapper<StockInfo> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(StockInfo::getIsDelete, 0)
                    .and(wrapper -> {
                        appendLikeConditions(wrapper, query);
                        if (CollUtil.isNotEmpty(keywords)) {
                            for (String keyword : keywords) {
                                appendLikeConditions(wrapper, keyword);
                            }
                        }
                    })
                    .orderByDesc(StockInfo::getHotScore)
                    .orderByDesc(StockInfo::getUpdateTime)
                    .last("limit " + MYSQL_LIMIT);

            return stockInfoMapper.selectList(queryWrapper).stream()
                    .map(this::toRagDocument)
                    .toList();
        } catch (Exception e) {
            log.warn("MySQL 股票结构化检索不可用，已降级为空结果：{}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 给多个结构化字段追加 like 查询条件。
     *
     * <p>这里同时查股票代码、股票名称、行业、主题、风险标签、关注理由，
     * 让用户输入“算力”“机器人”“高波动”等词时都能命中相关记录。</p>
     */
    private void appendLikeConditions(LambdaQueryWrapper<StockInfo> wrapper, String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return;
        }
        wrapper.or().like(StockInfo::getStockCode, keyword)
                .or().like(StockInfo::getStockName, keyword)
                .or().like(StockInfo::getIndustry, keyword)
                .or().like(StockInfo::getThemes, keyword)
                .or().like(StockInfo::getRiskTags, keyword)
                .or().like(StockInfo::getReason, keyword);
    }

    /**
     * 把 stock_info 表中的一条记录转换成统一 RAG 文档。
     */
    private StockRagDocument toRagDocument(StockInfo stockInfo) {
        List<String> lines = new ArrayList<>();
        lines.add("股票：" + stockInfo.getStockName() + "（" + stockInfo.getStockCode() + "）");
        lines.add("市场：" + StrUtil.blankToDefault(stockInfo.getMarket(), "A股"));
        lines.add("行业：" + StrUtil.blankToDefault(stockInfo.getIndustry(), "未知"));
        lines.add("主题：" + StrUtil.blankToDefault(stockInfo.getThemes(), "未标注"));
        lines.add("风险标签：" + StrUtil.blankToDefault(stockInfo.getRiskTags(), "未标注"));
        lines.add("关注理由：" + StrUtil.blankToDefault(stockInfo.getReason(), "暂无"));
        return StockRagDocument.builder()
                .sourceType("mysql")
                .sourceName("stock_info")
                .groupName("股票")
                .content(String.join("\n", lines))
                .weight(1.2)
                .riskLevel(detectRiskLevel(stockInfo.getRiskTags()))
                .build();
    }

    /**
     * 根据风险标签推断风险等级。
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
