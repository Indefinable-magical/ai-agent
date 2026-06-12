package com.yu.aiagent.advisor;

import com.yu.aiagent.exception.BusinessException;
import com.yu.aiagent.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 违禁词校验 Advisor：在请求真正发送给大模型前，先拦截不合规输入。
 *
 * <p>这个类同时实现了 {@link CallAdvisor} 和 {@link StreamAdvisor}，所以普通同步调用和
 * SSE 流式调用都会经过同一套校验逻辑。这样可以避免某个接口漏接入安全检查。</p>
 */
@Slf4j
public class SensitiveWordAdvisor implements CallAdvisor, StreamAdvisor {

    /**
     * 命中违禁词后统一返回给用户的提示。
     *
     * <p>不要把具体命中的词返回给前端，否则用户很容易根据提示反推规则并继续尝试绕过。</p>
     */
    private static final String BLOCK_MESSAGE = "输入内容包含不合规词，请修改后再试";

    /**
     * 违禁词配置文件。
     *
     * <p>文件位置：src/main/resources/sensitive-words.txt。约定每行一个词，空行和 # 开头的
     * 注释行会被忽略。</p>
     */
    private static final String WORDS_FILE = "sensitive-words.txt";

    /**
     * Agent 内部追加的下一步提示词。
     *
     * <p>这类提示词不是用户真实输入，不应该进入违禁词检查。
     * 例如提示词中出现工具名 askUser 时，可能会和 sk- 这类短英文规则产生误伤。</p>
     */
    private static final String MANUS_NEXT_STEP_PROMPT_FILE = "prompts/manus-next-step.st";

    /**
     * 英文压缩匹配的最小长度。
     *
     * <p>类似 sk- 这种规则在压缩后会变成 sk，如果继续做子串匹配，
     * 很容易误伤 askUser、task 等正常技术词。因此短英文规则只走标准文本匹配。</p>
     */
    private static final int MIN_ASCII_COMPACT_MATCH_LENGTH = 4;

    /**
     * Unicode 格式控制字符，例如零宽空格、零宽连接符等。
     *
     * <p>这些字符肉眼通常不可见，常被用于把 badword 写成 b​adword 之类的绕过形式。</p>
     */
    private static final Pattern ZERO_WIDTH_PATTERN = Pattern.compile("\\p{Cf}+");

    /**
     * 分隔符规则：空白、Unicode 分隔符、标点符号、符号。
     *
     * <p>用于生成“压缩文本”，把 b a d-word、b.a.d.w.o.r.d 等形式还原成 badword 再匹配。</p>
     */
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[\\s\\p{Z}\\p{P}\\p{S}]+");

    /**
     * 启动 Advisor 时加载并预处理好的规则列表。
     *
     * <p>每条规则会同时保存：
     * 原始词：用于日志排查；
     * 标准化词：用于大小写、全半角等常规匹配；
     * 压缩词：用于空格、标点、符号拆分绕过匹配。</p>
     */
    private final List<SensitiveWordRule> sensitiveWordRules;

    /**
     * 不参与检查的内部提示词集合。
     */
    private final Set<String> ignoredInternalPrompts;

    public SensitiveWordAdvisor() {
        // Advisor 被创建时只加载一次词表，避免每次请求都读取 resources 文件。
        this.sensitiveWordRules = loadSensitiveWordRules();
        this.ignoredInternalPrompts = loadIgnoredInternalPrompts();
    }

    /**
     * 从 resources 中读取违禁词文件，并预编译成便于匹配的规则对象。
     *
     * @return 过滤注释、空行并去重后的规则列表
     */
    private List<SensitiveWordRule> loadSensitiveWordRules() {
        try (InputStream inputStream = new ClassPathResource(WORDS_FILE).getInputStream()) {
            // 明确使用 UTF-8，保证中文违禁词能正确读取。
            String content = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);

            // 使用 LinkedHashSet 去重，同时保留文件中的顺序，方便按配置顺序排查日志。
            Set<String> distinctWords = Arrays.stream(content.split("\\R"))
                    .map(String::trim)
                    .filter(word -> !word.isEmpty())
                    .filter(word -> !word.startsWith("#"))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            return distinctWords.stream()
                    .map(this::toRule)
                    // 极端情况下，如果某个词只有标点或空格，压缩后会变成空字符串，直接丢弃。
                    .filter(rule -> !rule.normalizedWord().isBlank() || !rule.compactWord().isBlank())
                    .toList();
        } catch (Exception e) {
            // 词表加载失败时不阻断应用启动，但会降级为空词表并打印告警，便于本地排查。
            log.warn("违禁词文件加载失败，将使用空词表: {}", WORDS_FILE, e);
            return List.of();
        }
    }

    /**
     * 加载需要跳过检查的内部提示词。
     *
     * <p>目前主要用于 YuManus 的 next-step prompt。它以 UserMessage 形式进入模型上下文，
     * 但本质是系统内部控制指令，不是用户输入。</p>
     */
    private Set<String> loadIgnoredInternalPrompts() {
        try (InputStream inputStream = new ClassPathResource(MANUS_NEXT_STEP_PROMPT_FILE).getInputStream()) {
            String content = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8).trim();
            if (content.isBlank()) {
                return Set.of();
            }
            return Set.of(content);
        } catch (Exception e) {
            log.warn("内部提示词加载失败，将不启用内部提示词跳过规则: {}", MANUS_NEXT_STEP_PROMPT_FILE, e);
            return Set.of();
        }
    }

    /**
     * 把原始违禁词转换为可匹配规则。
     *
     * @param rawWord 词表中的原始词
     * @return 预处理后的规则
     */
    private SensitiveWordRule toRule(String rawWord) {
        NormalizedText normalizedText = normalize(rawWord);
        return new SensitiveWordRule(rawWord, normalizedText.normalized(), normalizedText.compact());
    }

    /**
     * 所有模型请求发送前都会经过这个方法。
     *
     * <p>如果命中违禁词，直接抛出业务异常，不再继续调用大模型。</p>
     *
     * @param request 当前 ChatClient 请求
     * @return 校验通过后的原请求
     */
    private ChatClientRequest before(ChatClientRequest request) {
        String userText = request.prompt().getInstructions().stream()
                // 只检查 UserMessage，不检查 system prompt、assistant 历史回复和工具返回内容。
                .filter(message -> message instanceof UserMessage)
                .map(Message::getText)
                .filter(text -> !isIgnoredInternalPrompt(text))
                // 多轮 Agent 会把历史用户消息和内部控制提示一并放入 Prompt。
                // 违禁词校验只应该针对“本轮最新用户输入”，否则历史中的旧内容会误伤后续正常问题。
                .reduce((previous, current) -> current)
                .orElse("");

        if (userText == null || userText.isBlank() || sensitiveWordRules.isEmpty()) {
            return request;
        }

        NormalizedText normalizedUserText = normalize(userText);
        sensitiveWordRules.stream()
                .filter(rule -> matches(normalizedUserText, rule))
                .findFirst()
                .ifPresent(rule -> {
                    // 日志只记录脱敏后的命中词，方便排查，也避免完整泄露违禁词。
                    log.warn("用户输入命中违禁词规则: {}", maskWord(rule.rawWord()));
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, BLOCK_MESSAGE);
                });
        return request;
    }

    /**
     * 判断用户输入是否命中某条规则。
     *
     * <p>标准化文本匹配负责处理大小写、全角半角；压缩文本匹配负责处理空格、标点、符号拆分。</p>
     */
    private boolean matches(NormalizedText userText, SensitiveWordRule rule) {
        boolean normalizedMatched = !rule.normalizedWord().isBlank()
                && userText.normalized().contains(rule.normalizedWord());
        boolean compactMatched = !rule.compactWord().isBlank()
                && canUseCompactMatch(rule.compactWord())
                && userText.compact().contains(rule.compactWord());
        return normalizedMatched || compactMatched;
    }

    /**
     * 判断某段文本是否是内部提示词。
     */
    private boolean isIgnoredInternalPrompt(String text) {
        return text != null && ignoredInternalPrompts.contains(text.trim());
    }

    /**
     * 判断某条规则是否允许使用压缩匹配。
     *
     * <p>中文或中英混合规则仍然允许短词压缩匹配；纯英文短规则不允许，
     * 避免 sk、ak 这类片段在正常单词中大量误伤。</p>
     */
    private boolean canUseCompactMatch(String compactWord) {
        if (!isAscii(compactWord)) {
            return true;
        }
        return compactWord.length() >= MIN_ASCII_COMPACT_MATCH_LENGTH;
    }

    private boolean isAscii(String text) {
        return text.chars().allMatch(ch -> ch <= 127);
    }

    /**
     * 文本归一化。
     *
     * <p>NFKC 会把全角字母、兼容字符等归一化为常见写法；再统一转小写并移除零宽字符，
     * 可以覆盖大多数“大小写/全半角/不可见字符”绕过形式。</p>
     */
    private NormalizedText normalize(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        normalized = ZERO_WIDTH_PATTERN.matcher(normalized).replaceAll("");
        String compact = SEPARATOR_PATTERN.matcher(normalized).replaceAll("");
        return new NormalizedText(normalized, compact);
    }

    /**
     * 脱敏显示命中的词。
     *
     * <p>例如 badword 会记录为 b*****d，中文短词会尽量只展示首尾字符。</p>
     */
    private String maskWord(String word) {
        if (word == null || word.isBlank()) {
            return "";
        }
        String trimmed = word.trim();
        int length = trimmed.length();
        if (length <= 1) {
            return "*";
        }
        if (length == 2) {
            return trimmed.charAt(0) + "*";
        }
        return trimmed.charAt(0) + "*".repeat(length - 2) + trimmed.charAt(length - 1);
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
        // 普通同步调用：先执行违禁词校验，通过后再交给后续 Advisor 或模型。
        return chain.nextCall(before(chatClientRequest));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
        // 流式调用：同样先校验，通过后才开始建立模型流。
        return chain.nextStream(before(chatClientRequest));
    }

    @Override
    public int getOrder() {
        // order 越小越早执行，违禁词校验应尽量靠前，避免后续流程处理不合规输入。
        return -100;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 单条违禁词规则。
     */
    private record SensitiveWordRule(String rawWord, String normalizedWord, String compactWord) {
    }

    /**
     * 用户输入或规则词的归一化结果。
     */
    private record NormalizedText(String normalized, String compact) {
    }
}
