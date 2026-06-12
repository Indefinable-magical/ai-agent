package com.yu.aiagent.rag.reader;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网页研报摘要读取器测试。
 */
class WebResearchDocumentReaderTest {

    @Test
    void readWebResearchPage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/research", exchange -> {
            String html = """
                    <!doctype html>
                    <html>
                    <head><title>AI算力行业研报摘要</title></head>
                    <body>
                    <h1>AI算力行业研报摘要</h1>
                    <p>光模块、CPO、AI服务器和高速PCB是AI算力产业链的重要方向。</p>
                    <p>相关股票研究需要同时关注订单节奏、估值波动和业绩兑现风险。</p>
                    </body>
                    </html>
                    """;
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });
        server.start();

        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/research";
            WebResearchDocumentReader reader = new WebResearchDocumentReader(
                    new SingleUrlResourcePatternResolver(url));

            List<Document> documents = reader.read();

            assertEquals(1, documents.size());
            Document document = documents.get(0);
            assertTrue(document.getText().contains("AI算力行业研报摘要"));
            assertTrue(document.getText().contains("光模块"));
            assertTrue(document.getText().contains("CPO"));
            assertEquals("web_research", document.getMetadata().get("sourceType"));
            assertEquals(url, document.getMetadata().get("url"));
            assertFalse(document.getText().isBlank());
        } finally {
            server.stop(0);
        }
    }

    /**
     * 给测试专用的 ResourcePatternResolver。
     *
     * <p>生产代码会读取 classpath:document/web-research-urls.txt；
     * 测试里为了不改真实配置文件，直接返回一段内存中的 URL 配置内容。</p>
     */
    private static class SingleUrlResourcePatternResolver extends PathMatchingResourcePatternResolver {

        private final String url;

        private SingleUrlResourcePatternResolver(String url) {
            this.url = url;
        }

        @Override
        public Resource[] getResources(String locationPattern) {
            ByteArrayResource resource = new ByteArrayResource(url.getBytes(StandardCharsets.UTF_8)) {
                @Override
                public String getFilename() {
                    return "web-research-urls.txt";
                }
            };
            return new Resource[]{resource};
        }
    }
}
