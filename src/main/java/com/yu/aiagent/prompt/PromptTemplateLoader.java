package com.yu.aiagent.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Prompt 模板加载器，用于从 resources/prompts 目录读取和渲染提示词模板。
 * <p>
 * 这样做的目的，是把大段提示词从 Java 业务代码中抽离出来：
 * <ul>
 *     <li>提示词可以像配置文件一样单独维护；</li>
 *     <li>修改提示词时不需要改动核心业务逻辑；</li>
 *     <li>不同应用、不同场景可以拆分成多个模板文件；</li>
 *     <li>需要变量时，可以通过 Spring AI 的 PromptTemplate 进行渲染。</li>
 * </ul>
 */
@Component
@Slf4j
public class PromptTemplateLoader {

    /**
     * 所有 Prompt 模板统一放在 classpath 下的 prompts 目录中。
     * <p>
     * 例如传入 stock-system.st 时，实际读取的是：
     * src/main/resources/prompts/stock-system.st
     */
    private static final String PROMPT_DIR = "prompts/";

    /**
     * 读取指定 Prompt 模板的原始文本。
     * <p>
     * 适用于不需要变量替换的场景，比如系统提示词、智能体下一步提示词。
     *
     * @param fileName resources/prompts 目录下的模板文件名
     * @return 模板文件中的完整文本内容
     */
    public String loadPrompt(String fileName) {
        // ClassPathResource 会从编译后的 classpath 中读取资源文件，
        // 因此应用打包成 jar 后仍然可以正常读取 resources/prompts 下的模板。
        try (InputStream inputStream = new ClassPathResource(PROMPT_DIR + fileName).getInputStream()) {
            // 明确使用 UTF-8，避免中文提示词在不同操作系统或运行环境下出现乱码。
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Prompt 是 AI 应用的关键配置，加载失败时不应该静默降级，
            // 直接抛出异常能让启动或调用阶段尽早暴露问题。
            log.error("Prompt 模板加载失败: {}", fileName, e);
            throw new IllegalStateException("Prompt 模板加载失败: " + fileName, e);
        }
    }

    /**
     * 读取并渲染包含变量的 Prompt 模板。
     * <p>
     * 模板中可以使用 {变量名} 占位，例如：
     * <pre>
     * {basePrompt}
     * 标题为 {userName} 的股票分析报告
     * </pre>
     * 调用时通过 variables 传入 basePrompt、userName 等变量值。
     *
     * @param fileName  resources/prompts 目录下的模板文件名
     * @param variables 模板变量映射
     * @return 完成变量替换后的 Prompt 文本
     */
    public String renderPrompt(String fileName, Map<String, Object> variables) {
        // Spring AI 的 PromptTemplate 负责解析模板变量并渲染最终文本。
        PromptTemplate promptTemplate = new PromptTemplate(loadPrompt(fileName));
        return promptTemplate.render(variables);
    }
}
