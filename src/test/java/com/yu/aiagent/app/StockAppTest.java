package com.yu.aiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class StockAppTest {

    @Resource
    private StockApp stockApp;

    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "你好，我是程序员小雨";
        String answer = stockApp.doChat(message, chatId);
        // 第二轮
        message = "我关注贵州茅台和宁德时代，想做中长期配置";
        answer = stockApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        // 第三轮
        message = "我刚才说过关注哪些股票？帮我回忆一下";
        answer = stockApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我是程序员小雨，我想分析一下贵州茅台的长期投资价值";
        StockApp.StockReport stockReport = stockApp.doChatWithReport(message, chatId);
        Assertions.assertNotNull(stockReport);
    }

    @Test
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "我想学习如何从财报判断一家公司的盈利质量";
        String answer = stockApp.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithTools() {
        // 测试联网搜索问题的答案
        testMessage("帮我搜索一下最近 A 股半导体板块有哪些重要新闻？");

        // 测试网页抓取：股票资讯分析
        testMessage("抓取一篇关于新能源车行业的公开资讯，并总结可能影响股价的因素");

        // 测试资源下载：图片下载
        testMessage("直接下载一张股票 K 线图示意图片为文件");

        // 测试终端操作：执行代码
        testMessage("执行 Python3 脚本来生成数据分析报告");

        // 测试文件操作：保存用户关注清单
        testMessage("保存我的股票关注清单为文件");

        // 测试 PDF 生成
        testMessage("生成一份‘股票复盘计划’PDF，包含关注标的、观察指标和风险提示");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = stockApp.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithMcp() {
        String chatId = UUID.randomUUID().toString();
        // 测试地图 MCP
//        String message = "帮我找一找上海附近有哪些上市公司总部";
//        String answer =  stockApp.doChatWithMcp(message, chatId);
//        Assertions.assertNotNull(answer);
        // 测试图片搜索 MCP
        String message = "帮我搜索一些股票走势图示意图片";
        String answer =  stockApp.doChatWithMcp(message, chatId);
        Assertions.assertNotNull(answer);
    }
}
