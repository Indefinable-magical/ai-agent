package com.yu.aiagent.tools;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PDFGenerationToolTest {

    @Test
    void generatePDF() throws IOException {
        PDFGenerationTool tool = new PDFGenerationTool();
        String fileName = "Python_入门学习计划_测试.pdf";
        String content = """
                # Python 入门学习计划
                ## 第1周：基础入门
                - Day 1：安装 Python，运行 print("Hello, Python!")
                - Day 2：学习变量、字符串、数字和布尔值
                推荐资源：https://docs.python.org/zh-cn/3/tutorial/
                """;

        String result = tool.generatePDF(fileName, content);

        assertNotNull(result);
        assertTrue(result.startsWith("PDF generated successfully to: "), result);
        String filePath = result.replace("PDF generated successfully to: ", "");
        assertTrue(Files.exists(Path.of(filePath)));
        assertTrue(Files.size(Path.of(filePath)) > 0);
    }
}
