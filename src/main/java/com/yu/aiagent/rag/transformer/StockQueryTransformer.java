package com.yu.aiagent.rag.transformer;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 股票问题转换器。
 *
 * <p>它位于 RAG 检索之前，负责把用户的口语化问题转换成更适合检索的形式。
 * 比如用户问“算力有什么票”，直接拿这句话检索可能命中文档不稳定；
 * 转换后会补充“AI 算力、光模块、CPO、AI 服务器、PCB”等关键词，
 * 向量检索和 MySQL 检索的命中率都会更高。</p>
 *
 * <p>这里故意使用规则而不是再调用一次大模型，优点是成本低、速度快、结果可控，
 * 也方便你后续按 A 股主线继续维护关键词表。</p>
 */
@Component
public class StockQueryTransformer {

    /**
     * 关键词扩展表。
     *
     * <p>key 是用户可能输入的口语词，value 是用于检索的专业扩展词。
     * 后续想加强某个主线，只需要继续往这里补关键词即可。</p>
     */
    private static final Map<String, List<String>> KEYWORD_EXPANSION_MAP = new LinkedHashMap<>();

    static {
        KEYWORD_EXPANSION_MAP.put("算力", List.of("AI算力", "光模块", "CPO", "AI服务器", "液冷", "PCB", "高速互联", "数据中心"));
        KEYWORD_EXPANSION_MAP.put("光模块", List.of("CPO", "高速光通信", "800G", "1.6T", "数据中心", "AI算力"));
        KEYWORD_EXPANSION_MAP.put("机器人", List.of("人形机器人", "丝杠", "减速器", "电机", "传感器", "智能制造"));
        KEYWORD_EXPANSION_MAP.put("低空", List.of("低空经济", "通航运营", "无人机", "飞行汽车", "空管系统"));
        KEYWORD_EXPANSION_MAP.put("半导体", List.of("国产替代", "AI芯片", "设备", "材料", "先进封装"));
        KEYWORD_EXPANSION_MAP.put("电力", List.of("智能电网", "特高压", "电力设备", "虚拟电厂", "储能"));
        KEYWORD_EXPANSION_MAP.put("妖股", List.of("高波动", "连板", "题材炒作", "情绪周期", "换手率", "退潮风险"));
        KEYWORD_EXPANSION_MAP.put("趋势票", List.of("机构趋势", "业绩确定性", "均线趋势", "成交量", "行业景气"));
        KEYWORD_EXPANSION_MAP.put("机构票", List.of("机构重仓", "大市值", "业绩兑现", "趋势股", "行业龙头"));
        KEYWORD_EXPANSION_MAP.put("风险", List.of("估值风险", "业绩风险", "题材退潮", "流动性风险", "监管风险"));
    }

    /**
     * 对用户问题做完整转换。
     *
     * <p>转换结果包含 4 部分：</p>
     * <p>1. originalQuery：用户原始问题。</p>
     * <p>2. rewrittenQuery：拼接了意图和扩展关键词后的检索问题。</p>
     * <p>3. expandedKeywords：扩展出的关键词列表，供 MySQL 检索使用。</p>
     * <p>4. intent：识别出的用户意图，供风险过滤和回答结构使用。</p>
     */
    public StockQueryTransformResult transform(String query) {
        // 先做空值保护和去空格，避免后续规则判断出现空指针或无意义检索。
        String normalizedQuery = StrUtil.blankToDefault(query, "").trim();

        // 判断用户到底是想找股票、看行业、看主题，还是分析风险。
        StockQueryIntent intent = recognizeIntent(normalizedQuery);

        // 根据用户输入和识别到的意图扩展检索关键词。
        List<String> expandedKeywords = expandKeywords(normalizedQuery, intent);

        // 把原问题、意图、关键词组织成更适合向量检索的查询文本。
        String rewrittenQuery = rewriteQuery(normalizedQuery, expandedKeywords, intent);

        return StockQueryTransformResult.builder()
                .originalQuery(normalizedQuery)
                .rewrittenQuery(rewrittenQuery)
                .expandedKeywords(expandedKeywords)
                .intent(intent)
                .build();
    }

    /**
     * 识别用户问题意图。
     *
     * <p>意图识别会影响后面的风险过滤。比如用户没有明确说“妖股”或“高波动”，
     * 系统会尽量减少高风险内容的注入。</p>
     */
    private StockQueryIntent recognizeIntent(String query) {
        if (StrUtil.isBlank(query)) {
            return StockQueryIntent.GENERAL;
        }
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        if (StrUtil.containsAny(query, "风险", "回撤", "退潮", "高估", "亏损", "监管")) {
            return StockQueryIntent.RISK_ANALYSIS;
        }
        if (StrUtil.containsAny(query, "行业", "产业链", "板块", "赛道")) {
            return StockQueryIntent.INDUSTRY_RESEARCH;
        }
        if (StrUtil.containsAny(query, "主题", "主线", "题材", "方向")) {
            return StockQueryIntent.THEME_RESEARCH;
        }
        if (StrUtil.containsAny(query, "股票", "票", "标的", "妖股", "趋势票", "机构票")) {
            return StockQueryIntent.STOCK_DISCOVERY;
        }
        if (lowerQuery.matches(".*\\b(sz|sh)?\\d{6}\\b.*") || StrUtil.containsAny(query, "公司", "财报", "估值", "基本面")) {
            return StockQueryIntent.COMPANY_ANALYSIS;
        }
        return StockQueryIntent.GENERAL;
    }

    /**
     * 扩展检索关键词。
     *
     * <p>使用 LinkedHashSet 是为了去重并保持插入顺序，让改写后的查询稳定可读。</p>
     */
    private List<String> expandKeywords(String query, StockQueryIntent intent) {
        Set<String> keywords = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : KEYWORD_EXPANSION_MAP.entrySet()) {
            if (StrUtil.containsIgnoreCase(query, entry.getKey())) {
                keywords.add(entry.getKey());
                keywords.addAll(entry.getValue());
            }
        }
        if (intent == StockQueryIntent.STOCK_DISCOVERY) {
            keywords.addAll(List.of("股票", "关注对象", "行业地位", "风险标签"));
        }
        if (intent == StockQueryIntent.RISK_ANALYSIS) {
            keywords.addAll(List.of("风险标签", "估值", "业绩兑现", "题材退潮"));
        }
        return new ArrayList<>(keywords);
    }

    /**
     * 生成最终用于检索的查询文本。
     *
     * <p>向量库更擅长理解一段完整语义文本，所以这里不是只返回关键词，
     * 而是把原始问题、意图、关键词、检索要求一起拼成一段文本。</p>
     */
    private String rewriteQuery(String query, List<String> expandedKeywords, StockQueryIntent intent) {
        List<String> fragments = new ArrayList<>();
        fragments.add(query);
        fragments.add("意图：" + intent.name());
        if (CollUtil.isNotEmpty(expandedKeywords)) {
            fragments.add("扩展关键词：" + String.join("、", expandedKeywords));
        }
        fragments.add("请优先检索股票、行业、主题、风险标签相关知识。");
        return String.join("\n", fragments);
    }
}
