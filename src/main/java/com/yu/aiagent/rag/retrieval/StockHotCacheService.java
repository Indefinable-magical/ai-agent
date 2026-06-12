package com.yu.aiagent.rag.retrieval;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Redis 热门问题和热门股票缓存服务。
 *
 * <p>Redis 在混合检索里不是主知识库，而是补充热点信息：
 * 用户经常问什么、哪些股票经常被检索，都可以记录下来。</p>
 */
@Service
@Slf4j
public class StockHotCacheService {

    /**
     * 热门问题 ZSet key。
     *
     * <p>member 是用户问题，score 是出现次数。</p>
     */
    private static final String HOT_QUERY_KEY = "stock:rag:hot:query";

    /**
     * 热门股票 ZSet key。
     *
     * <p>member 是股票名称，score 是命中次数。</p>
     */
    private static final String HOT_STOCK_KEY = "stock:rag:hot:stock";

    private final StringRedisTemplate stringRedisTemplate;

    public StockHotCacheService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 记录用户问题热度。
     */
    public void recordQuery(String query) {
        if (StrUtil.isBlank(query)) {
            return;
        }
        try {
            stringRedisTemplate.opsForZSet().incrementScore(HOT_QUERY_KEY, query, 1);
        } catch (Exception e) {
            // Redis 只是增强能力，失败时不能影响 AI 主流程。
            log.warn("Redis 记录热门问题失败：{}", e.getMessage());
        }
    }

    /**
     * 记录股票热度。
     */
    public void recordStock(String stockName) {
        if (StrUtil.isBlank(stockName)) {
            return;
        }
        try {
            stringRedisTemplate.opsForZSet().incrementScore(HOT_STOCK_KEY, stockName, 1);
        } catch (Exception e) {
            log.warn("Redis 记录热门股票失败：{}", e.getMessage());
        }
    }

    /**
     * 查询 Redis 热门缓存，转换成 RAG 文档。
     *
     * <p>这样热门问题、热门股票也可以作为上下文补充给大模型。</p>
     */
    public List<StockRagDocument> searchHotItems() {
        try {
            return List.of(
                    buildHotDocument("热门问题", HOT_QUERY_KEY),
                    buildHotDocument("热门股票", HOT_STOCK_KEY)
            ).stream().filter(document -> StrUtil.isNotBlank(document.getContent())).toList();
        } catch (Exception e) {
            log.warn("Redis 热门缓存检索失败：{}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 从指定 ZSet 中读取前 5 个热点，并包装成 StockRagDocument。
     */
    private StockRagDocument buildHotDocument(String title, String key) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(key, 0, 4);
        if (tuples == null || tuples.isEmpty()) {
            return StockRagDocument.builder()
                    .sourceType("redis")
                    .sourceName(key)
                    .groupName("主题")
                    .content("")
                    .weight(0.3)
                    .riskLevel("low")
                    .build();
        }

        StringBuilder builder = new StringBuilder(title).append("：\n");
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            builder.append("- ")
                    .append(tuple.getValue())
                    .append("，热度 ")
                    .append(tuple.getScore())
                    .append('\n');
        }
        return StockRagDocument.builder()
                .sourceType("redis")
                .sourceName(key)
                .groupName("主题")
                .content(builder.toString())
                .weight(0.3)
                .riskLevel("low")
                .build();
    }
}
