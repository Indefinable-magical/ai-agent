package com.yu.aiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.yu.aiagent.agent.model.AgentLoopDetector;
import com.yu.aiagent.agent.model.AgentStepEvent;
import com.yu.aiagent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * AI Agent 的抽象基础类。
 *
 * <p>这个类只负责“通用执行框架”，不关心具体 Agent 每一步到底怎么思考、怎么调用工具。
 * 具体的一步执行逻辑由子类实现 {@link #step()} 方法。</p>
 *
 * <p>它主要管理 4 件事：</p>
 * <ul>
 *     <li>Agent 状态：IDLE、RUNNING、FINISHED、ERROR。</li>
 *     <li>会话上下文：通过 messageList 保存用户消息、AI 回复、工具调用相关消息。</li>
 *     <li>执行循环：最多执行 maxSteps 步，直到子类把状态改为 FINISHED 或 ERROR。</li>
 *     <li>结果持久化：运行结束后通过 messageListSaver 回调把会话保存到外部存储。</li>
 * </ul>
 */
@Data
@Slf4j
public abstract class BaseAgent {

    /**
     * Agent 名称，主要用于日志输出，比如 YuManus。
     */
    private String name;

    /**
     * 系统提示词。
     *
     * <p>它定义 Agent 的身份、职责、能力边界和回答规则。
     * 在真正调用大模型时，子类通常会把它作为 system prompt 传给 ChatClient。</p>
     */
    private String systemPrompt;

    /**
     * 下一步提示词。
     *
     * <p>多步 Agent 每执行一步前，可能会额外追加一条固定提示，
     * 引导模型继续判断“下一步是否需要调用工具、是否应该结束任务”。</p>
     */
    private String nextStepPrompt;

    /**
     * 当前 Agent 状态。
     *
     * <p>新建 Agent 默认为 IDLE；开始运行后变为 RUNNING；
     * 子类完成任务后应设置为 FINISHED；遇到异常时设置为 ERROR。</p>
     */
    private AgentState state = AgentState.IDLE;

    /**
     * 当前执行到第几步。
     *
     * <p>这个字段主要用于日志、调试和最大步骤判断。</p>
     */
    private int currentStep = 0;

    /**
     * 单次运行最多执行多少步。
     *
     * <p>这是一个兜底保护，避免 Agent 因为一直无法结束而无限循环。</p>
     */
    private int maxSteps = 10;

    /**
     * Spring AI 的 ChatClient。
     *
     * <p>BaseAgent 不直接调用它，具体调用方式由子类决定。
     * 比如 ToolCallAgent 会用它发起模型请求并处理工具调用。</p>
     */
    private ChatClient chatClient;

    /**
     * 当前会话的完整上下文消息。
     *
     * <p>这里保存的是 Spring AI 的 Message 对象，包括用户消息、AI 消息、系统消息等。
     * 对于支持工具调用的 Agent，这里面也可能短暂包含工具调用过程中的内部消息。</p>
     */
    private List<Message> messageList = new ArrayList<>();

    /**
     * 会话保存回调。
     *
     * <p>BaseAgent 不直接依赖 MySQL、Redis 或文件系统，而是通过回调把消息列表交给外部业务层。
     * 这样 Agent 执行逻辑和持久化方式可以解耦。</p>
     */
    private Consumer<List<Message>> messageListSaver;

    /**
     * Agent 过程事件回调。
     *
     * <p>聊天正文仍然走原来的 SSE message 通道；这里单独回调结构化步骤事件，
     * 让前端可以渲染任务时间线，而不会把过程信息混入最终回答。</p>
     */
    private Consumer<AgentStepEvent> stepEventConsumer;

    /**
     * 循环检测器，用于识别连续重复的执行步骤。
     *
     * <p>默认规则是连续 3 次相同签名触发保护，签名最多保留 240 个字符。
     * 子类可以通过重写 {@link #buildLoopSignature(String)} 决定“什么算同一个步骤”。</p>
     */
    private AgentLoopDetector loopDetector = new AgentLoopDetector(3, 240);

    /**
     * 同步运行 Agent。
     *
     * <p>适合测试或非流式场景。方法会阻塞到 Agent 执行结束，然后一次性返回所有步骤结果。</p>
     *
     * @param userPrompt 用户本轮输入
     * @return 每一步执行结果拼接后的文本
     */
    public String run(String userPrompt) {
        // 同一个 Agent 实例不能在非空闲状态下重复启动，避免上下文和状态混乱。
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        // 空输入没有执行意义，直接拒绝。
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }

        // 标记进入运行中，并把用户本轮输入加入上下文。
        this.state = AgentState.RUNNING;
        loopDetector.reset();
        messageList.add(new UserMessage(userPrompt));

        // 保存每一步返回结果，最终合并成一个完整字符串。
        List<String> results = new ArrayList<>();

        try {
            // 只要没有达到最大步数，并且状态仍为 RUNNING，就持续执行子类定义的 step。
            for (int i = 0; i < maxSteps && state == AgentState.RUNNING; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxSteps);

                String stepResult = step();
                results.add("Step " + stepNumber + ": " + stepResult);

                // 每一步完成后立刻做循环检测；如果已经重复到阈值，就暂停任务并等待用户反馈。
                if (isLoopDetected(stepResult)) {
                    String loopMessage = handleLoopDetected();
                    results.add("Loop detected: " + loopMessage);
                    break;
                }
            }

            // 如果跑满最大步数后仍未结束，强制标记为完成，避免无限循环。
            if (state == AgentState.RUNNING && currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            // 未被子类处理的异常会进入这里，统一标记为 ERROR 并返回错误信息。
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            return "执行错误：" + e.getMessage();
        } finally {
            // 无论正常结束还是异常结束，都尝试保存会话并清理资源。
            saveMessageList();
            this.cleanup();
        }
    }

    /**
     * 流式运行 Agent。
     *
     * <p>适合前端聊天页面使用。每执行完一步，就通过 SSE 把该步结果推送给前端。</p>
     *
     * @param userPrompt 用户本轮输入
     * @return Spring MVC 的 SSE 推送器
     */
    public SseEmitter runStream(String userPrompt) {
        // 设置较长超时时间，避免复杂任务还没完成 SSE 就断开。
        SseEmitter sseEmitter = new SseEmitter(300000L);
        // 将结构化步骤事件写入命名 SSE 事件，前端通过 addEventListener("agent-step") 单独消费。
        this.stepEventConsumer = event -> {
            try {
                sseEmitter.send(SseEmitter.event().name("agent-step").data(event));
            } catch (IOException e) {
                log.warn("发送 Agent 步骤事件失败", e);
            }
        };

        // Agent 执行可能耗时较长，放到异步线程中处理，避免阻塞 Web 请求线程。
        CompletableFuture.runAsync(() -> {
            try {
                if (this.state != AgentState.IDLE) {
                    sseEmitter.send("错误：无法从当前状态运行代理：" + this.state);
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sseEmitter.send("错误：不能使用空提示词运行代理");
                    sseEmitter.complete();
                    return;
                }
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
                return;
            }

            this.state = AgentState.RUNNING;
            loopDetector.reset();
            messageList.add(new UserMessage(userPrompt));
            List<String> results = new ArrayList<>();

            try {
                emitStepEvent(0, "thinking", "任务已接收", "正在理解你的需求并准备规划执行步骤", "running");
                for (int i = 0; i < maxSteps && state == AgentState.RUNNING; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("Executing step {}/{}", stepNumber, maxSteps);
                    emitStepEvent(stepNumber, "thinking", "思考下一步", "正在判断是否需要调用工具或生成最终回复", "running");

                    String stepResult = step();
                    results.add("Step " + stepNumber + ": " + stepResult);

                    // stepResult 为空通常表示本步只是内部工具调用，不需要推送给用户。
                    if (StrUtil.isNotBlank(stepResult)) {
                        sseEmitter.send(stepResult);
                    }

                    // 流式场景下同样做循环检测，触发后通过 SSE 立即把暂停原因推给前端。
                    if (isLoopDetected(stepResult)) {
                        String loopMessage = handleLoopDetected();
                        sseEmitter.send(loopMessage);
                        results.add("Loop detected: " + loopMessage);
                        break;
                    }
                }

                if (state == AgentState.RUNNING && currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    results.add("Terminated: Reached max steps (" + maxSteps + ")");
                    emitStepEvent(currentStep, "generating_report", "达到最大步骤", "已达到最大执行步数，准备结束本次任务", "success");
                    sseEmitter.send("执行结束：达到最大步骤（" + maxSteps + "）");
                }

                // 给前端一个明确结束标记，方便关闭 EventSource 或刷新会话列表。
                if (state == AgentState.WAITING_USER) {
                    // WAITING_USER 不代表失败，而是主动暂停；前端会展示“等待反馈”的时间线状态。
                    emitStepEvent(currentStep, "interaction_required", "等待用户反馈",
                            "智能体已暂停执行，等待你补充信息或确认下一步", "running");
                } else {
                    emitStepEvent(currentStep, "done", "任务完成", "执行流程已结束，正在整理最终回复", "success");
                }
                sseEmitter.send("[DONE]");
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing agent", e);
                try {
                    emitStepEvent(currentStep, "error", "执行异常", e.getMessage(), "error");
                    sseEmitter.send("执行错误：" + e.getMessage());
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                saveMessageList();
                this.cleanup();
            }
        });

        // 超时代表连接异常结束，标记为 ERROR 并交给子类释放资源。
        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.stepEventConsumer = null;
            this.cleanup();
            log.warn("SSE connection timeout");
        });

        // 浏览器主动关闭连接或后端 complete 后都会触发 completion。
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.stepEventConsumer = null;
            this.cleanup();
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }

    /**
     * 定义 Agent 的单步执行逻辑。
     *
     * <p>BaseAgent 不知道具体怎么执行一步，所以把这个方法留给子类实现。
     * 例如 ReActAgent 会在一步里组合 think 和 act，ToolCallAgent 会在其中处理工具调用。</p>
     *
     * @return 当前步骤需要返回给调用方或前端的结果
     */
    public abstract String step();

    /**
     * 构造当前步骤的循环检测签名。
     *
     * <p>默认使用步骤输出；工具型 Agent 可以重写为“工具名称 + 参数”的签名。</p>
     *
     * @param stepResult 当前步骤返回给调用方的文本
     * @return 用于循环检测的标准签名
     */
    protected String buildLoopSignature(String stepResult) {
        return stepResult;
    }

    /**
     * 判断当前步骤是否触发循环保护。
     *
     * <p>BaseAgent 只负责统一调度，真正的签名内容由 {@link #buildLoopSignature(String)} 提供。</p>
     */
    private boolean isLoopDetected(String stepResult) {
        return loopDetector.recordAndCheck(buildLoopSignature(stepResult));
    }

    /**
     * 处理循环命中后的暂停逻辑。
     *
     * <p>这里会把状态改成 WAITING_USER，并把提示语写入消息列表。
     * 这样历史会话里能保留“为什么暂停”的上下文，用户下一轮补充信息时也能接着往下做。</p>
     */
    private String handleLoopDetected() {
        state = AgentState.WAITING_USER;
        String message = "我检测到当前任务的执行步骤在重复。为避免陷入无效循环，我先暂停下来。"
                + "请补充更具体的目标、缺失的信息，或确认是否继续当前方向。";
        messageList.add(new AssistantMessage(message));
        emitStepEvent(currentStep, "loop_detected", "检测到重复执行",
                "连续 " + loopDetector.getRepeatCount() + " 次出现相同执行动作，已暂停等待用户反馈", "error");
        return message;
    }

    /**
     * 清理资源的扩展点。
     *
     * <p>默认不做任何事情。子类如果持有文件、网络连接、临时资源等，可以重写这个方法释放。</p>
     */
    protected void cleanup() {
    }

    /**
     * 推送 Agent 执行过程事件。
     *
     * <p>子类只需要描述当前发生了什么，具体如何写入 SSE 由 runStream 中设置的回调处理。</p>
     */
    protected void emitStepEvent(Integer step, String type, String title, String description, String status) {
        if (stepEventConsumer == null) {
            return;
        }
        stepEventConsumer.accept(new AgentStepEvent(step, type, title, description, status, System.currentTimeMillis()));
    }

    /**
     * 保存当前消息列表。
     *
     * <p>这里传入的是 messageList 的快照，而不是原始引用。
     * 这样即使后续异步逻辑继续修改 messageList，也不会影响已经交给持久化层的那份数据。</p>
     */
    private void saveMessageList() {
        if (messageListSaver != null) {
            messageListSaver.accept(new ArrayList<>(messageList));
        }
    }
}
