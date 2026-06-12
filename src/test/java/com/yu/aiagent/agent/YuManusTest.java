package com.yu.aiagent.agent;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class YuManusTest {

    @Resource
    private YuManus manus;

    @Test
    public void run() {
        String userPrompt = """
                请帮我调研上海静安区附近有哪些上市公司或金融机构，
                并结合一些网络图片，制定一份股票行业调研计划，
                并以 PDF 格式输出""";
        String answer = manus.run(userPrompt);
        Assertions.assertNotNull(answer);
    }
}
