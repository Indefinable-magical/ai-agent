package com.yu.aiagent.controller;

import com.yu.aiagent.annotation.AuthCheck;
import com.yu.aiagent.agent.YuManus;
import com.yu.aiagent.app.StockApp;
import com.yu.aiagent.chatmemory.ManusChatMemoryManager;
import com.yu.aiagent.chatmemory.MysqlChatMemoryRepository;
import com.yu.aiagent.common.BaseResponse;
import com.yu.aiagent.common.ResultUtils;
import com.yu.aiagent.exception.ErrorCode;
import com.yu.aiagent.exception.ThrowUtils;
import com.yu.aiagent.model.dto.chat.ChatRequest;
import com.yu.aiagent.model.dto.chat.ConversationRenameRequest;
import com.yu.aiagent.model.entity.User;
import com.yu.aiagent.model.vo.ManusConversationVO;
import com.yu.aiagent.model.vo.ManusMessageVO;
import com.yu.aiagent.model.vo.StockConversationVO;
import com.yu.aiagent.model.vo.StockMessageVO;
import com.yu.aiagent.prompt.PromptTemplateLoader;
import com.yu.aiagent.service.ChatMemoryService;
import com.yu.aiagent.service.StockUserPreferenceService;
import com.yu.aiagent.service.StockWatchlistService;
import com.yu.aiagent.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private StockApp stockApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private PromptTemplateLoader promptTemplateLoader;

    @Resource
    private ChatMemoryService chatMemoryService;

    @Resource
    private UserService userService;

    @Resource
    private MysqlChatMemoryRepository mysqlChatMemoryRepository;

    @Resource
    private ManusChatMemoryManager manusChatMemoryManager;

    @Resource
    private StockWatchlistService stockWatchlistService;

    @Resource
    private StockUserPreferenceService stockUserPreferenceService;

    /**
     * 同步调用 AI 股票大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @AuthCheck
    @GetMapping("/stock_app/chat/sync")
    public String doChatWithStockAppSync(String message, String chatId, HttpServletRequest request) {
        String conversationId = normalizeChatId(chatId);
        bindStockConversationUser(conversationId, request);
        return stockApp.doChat(message, conversationId);
    }

    /**
     * SSE 流式调用 AI 股票大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @AuthCheck
    @GetMapping(value = "/stock_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithStockAppSSE(String message, String chatId, HttpServletRequest request) {
        String conversationId = normalizeChatId(chatId);
        User loginUser = bindStockConversationUser(conversationId, request);
        return stockApp.doChatByStream(message, conversationId, buildStockUserContext(loginUser.getId()));
    }

    /**
     * SSE 流式调用 AI 股票大师应用（POST 版本）。
     *
     * <p>较长的工作台提示词放在请求体中，避免 GET query 过长触发 Tomcat 请求头限制。</p>
     */
    @AuthCheck
    @PostMapping(value = "/stock_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithStockAppSSEByPost(@RequestBody ChatRequest chatRequest,
                                                    HttpServletRequest request) {
        ThrowUtils.throwIf(chatRequest == null, ErrorCode.PARAMS_ERROR);
        String conversationId = normalizeChatId(chatRequest.getChatId());
        User loginUser = bindStockConversationUser(conversationId, request);
        return stockApp.doChatByStream(
                chatRequest.getMessage(),
                conversationId,
                buildStockUserContext(loginUser.getId())
        );
    }

    /**
     * 获取当前登录用户的 AI 股票大师历史会话列表。
     */
    @AuthCheck
    @GetMapping("/stock_app/conversations")
    public BaseResponse<List<StockConversationVO>> listStockConversations(@RequestParam(required = false) String keyword,
                                                                          HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(chatMemoryService.listStockConversations(loginUser.getId(), keyword));
    }

    /**
     * 获取指定会话的历史消息，用于前端回显旧对话。
     */
    @AuthCheck
    @GetMapping("/stock_app/conversations/{conversationId}/messages")
    public BaseResponse<List<StockMessageVO>> getStockConversationMessages(@PathVariable String conversationId,
                                                                           HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(chatMemoryService.getStockConversationMessages(conversationId, loginUser.getId()));
    }

    /**
     * 删除指定股票大师会话。
     */
    @AuthCheck
    @DeleteMapping("/stock_app/conversations/{conversationId}")
    public BaseResponse<Boolean> deleteStockConversation(@PathVariable String conversationId,
                                                         HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(chatMemoryService.deleteStockConversation(conversationId, loginUser.getId()));
    }

    /**
     * 重命名指定股票大师会话。
     */
    @AuthCheck
    @PostMapping("/stock_app/conversations/{conversationId}/rename")
    public BaseResponse<Boolean> renameStockConversation(@PathVariable String conversationId,
                                                         @RequestBody ConversationRenameRequest renameRequest,
                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(renameRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(chatMemoryService.renameStockConversation(
                conversationId, loginUser.getId(), renameRequest.getTitle()));
    }

    /**
     * SSE 流式调用 AI 股票大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @AuthCheck
    @GetMapping(value = "/stock_app/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithStockAppServerSentEvent(String message,
                                                                          String chatId,
                                                                          HttpServletRequest request) {
        String conversationId = normalizeChatId(chatId);
        User loginUser = bindStockConversationUser(conversationId, request);
        return stockApp.doChatByStream(message, conversationId, buildStockUserContext(loginUser.getId()))
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * SSE 流式调用 AI 股票大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @AuthCheck
    @GetMapping(value = "/stock_app/chat/sse_emitter")
    public SseEmitter doChatWithStockAppServerSseEmitter(String message, String chatId, HttpServletRequest request) {
        String conversationId = normalizeChatId(chatId);
        User loginUser = bindStockConversationUser(conversationId, request);
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(180000L); // 3 分钟超时
        // 获取 Flux 响应式数据流并且直接通过订阅推送给 SseEmitter
        stockApp.doChatByStream(message, conversationId, buildStockUserContext(loginUser.getId()))
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        // 返回
        return sseEmitter;
    }

    private User bindStockConversationUser(String chatId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        mysqlChatMemoryRepository.bindConversationUser(chatId, loginUser.getId());
        return loginUser;
    }

    private String normalizeChatId(String chatId) {
        return chatId == null || chatId.isBlank()
                ? "stock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                : chatId.trim();
    }

    /**
     * 构建股票大师专用的用户上下文。
     *
     * <p>这里集中拼接用户风险偏好和自选股，保证多个股票大师 SSE 入口使用同一套上下文。</p>
     */
    private String buildStockUserContext(Long userId) {
        return stockUserPreferenceService.buildRiskPreferenceContext(userId)
                + "\n" + stockWatchlistService.buildWatchlistContext(userId);
    }

    /**
     * 流式调用 Manus 超级智能体
     *
     * @param message
     * @return
     */
    @AuthCheck
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message, String chatId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        YuManus manus = new YuManus(allTools, dashscopeChatModel, promptTemplateLoader);
        manusChatMemoryManager.attachMemory(manus, chatId, loginUser.getId());
        return manus.runStream(message);
    }

    /**
     * 获取当前登录用户的 AI 超级智能体历史会话列表。
     */
    @AuthCheck
    @GetMapping("/manus/conversations")
    public BaseResponse<List<ManusConversationVO>> listManusConversations(@RequestParam(required = false) String keyword,
                                                                          HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(chatMemoryService.listManusConversations(loginUser.getId(), keyword));
    }

    /**
     * 获取指定 AI 超级智能体会话的历史消息。
     */
    @AuthCheck
    @GetMapping("/manus/conversations/{conversationId}/messages")
    public BaseResponse<List<ManusMessageVO>> getManusConversationMessages(@PathVariable String conversationId,
                                                                           HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(chatMemoryService.getManusConversationMessages(conversationId, loginUser.getId()));
    }

    /**
     * 软删除指定 AI 超级智能体会话。
     */
    @AuthCheck
    @DeleteMapping("/manus/conversations/{conversationId}")
    public BaseResponse<Boolean> deleteManusConversation(@PathVariable String conversationId,
                                                         HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(chatMemoryService.deleteManusConversation(conversationId, loginUser.getId()));
    }

    /**
     * 重命名指定超级智能体会话。
     */
    @AuthCheck
    @PostMapping("/manus/conversations/{conversationId}/rename")
    public BaseResponse<Boolean> renameManusConversation(@PathVariable String conversationId,
                                                         @RequestBody ConversationRenameRequest renameRequest,
                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(renameRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(chatMemoryService.renameManusConversation(
                conversationId, loginUser.getId(), renameRequest.getTitle()));
    }

}
