package com.yu.aiagent.rag.reader;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网页研报摘要读取器。
 *
 * <p>读取 src/main/resources/document/web-research-urls.txt。
 * 文件里每一行是一个网页 URL，启动时会抓取网页正文，转换成 RAG 文档。</p>
 */
@Component
@Order(40)
@Slf4j
public class WebResearchDocumentReader implements StockDocumentReader {

    /**
     * 单个网页最多保留的正文字符数。
     */
    private static final int MAX_TEXT_LENGTH = 10000;

    private final ResourcePatternResolver resourcePatternResolver;

    public WebResearchDocumentReader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    @Override
    public List<Document> read() {
        List<Document> documents = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/web-research-urls.txt");
            for (Resource resource : resources) {
                for (String url : readUrls(resource)) {
                    Document document = readWebPage(url);
                    if (document != null) {
                        documents.add(document);
                    }
                }
            }
        } catch (Exception e) {
            log.error("网页研报摘要加载失败", e);
        }
        return documents;
    }

    /**
     * 从 URL 配置文件中读取链接。
     *
     * <p>空行和 # 开头的行会被忽略，方便在配置文件中写注释。</p>
     */
    private List<String> readUrls(Resource resource) throws Exception {
        List<String> urls = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String url = line.trim();
                if (StrUtil.isBlank(url) || url.startsWith("#")) {
                    continue;
                }
                urls.add(url);
            }
        }
        return urls;
    }

    /**
     * 抓取网页正文，并转换成 Document。
     */
    private Document readWebPage(String url) {
        try {
            org.jsoup.nodes.Document htmlDocument = Jsoup.connect(url)
                    .timeout((int) Duration.ofSeconds(5).toMillis())
                    .userAgent("Mozilla/5.0")
                    .get();
            String title = htmlDocument.title();
            String text = StrUtil.subPre(htmlDocument.body().text(), MAX_TEXT_LENGTH);
            if (StrUtil.isBlank(text)) {
                return null;
            }

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("filename", url);
            metadata.put("sourceType", "web_research");
            metadata.put("sourceName", StrUtil.blankToDefault(title, url));
            metadata.put("url", url);
            metadata.put("riskLevel", "medium");
            return new Document("网页研报摘要：" + title + "\n来源：" + url + "\n" + text, metadata);
        } catch (Exception e) {
            log.warn("读取网页研报失败：{}", url, e);
            return null;
        }
    }
}
