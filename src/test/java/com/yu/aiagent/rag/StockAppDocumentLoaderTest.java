package com.yu.aiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StockAppDocumentLoaderTest {

    @Resource
    private StockAppDocumentLoader stockAppDocumentLoader;

    @Test
    void loadMarkdowns() {
        stockAppDocumentLoader.loadMarkdowns();
    }
}
