package com.yu.aiagent.agent.model;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;

/**
 * Agent 循环检测器。
 *
 * <p>用于识别连续重复的执行签名，避免智能体在相同动作上反复打转。
 * 这里不做复杂的语义相似度判断，只做轻量级的“相邻步骤重复”检测，
 * 这样成本低、可预测，也更适合作为执行框架的兜底保护。</p>
 */
public class AgentLoopDetector {

    /**
     * 连续重复多少次后判定为循环。
     *
     * <p>这个阈值不能太小，否则正常的“思考 -> 调用 -> 再思考”也可能误判；
     * 也不能太大，否则模型真的卡住时又会浪费太多轮次。</p>
     */
    private final int repeatThreshold;

    /**
     * 循环签名的最大长度。
     *
     * <p>工具参数可能非常长，直接保留完整内容没必要，截断后仍然足够识别“是否重复”。</p>
     */
    private final int maxSignatureLength;

    /**
     * 最近一次记录的执行签名。
     */
    private String lastSignature;

    /**
     * 当前签名连续重复的次数。
     */
    @Getter
    private int repeatCount;

    /**
     * 构造一个循环检测器。
     *
     * @param repeatThreshold 连续重复多少次后触发循环保护
     * @param maxSignatureLength 签名最大保留长度
     */
    public AgentLoopDetector(int repeatThreshold, int maxSignatureLength) {
        this.repeatThreshold = repeatThreshold;
        this.maxSignatureLength = maxSignatureLength;
    }

    /**
     * 记录本次执行签名，并判断是否进入循环。
     *
     * <p>如果当前签名和上一次完全一致，就认为重复次数 +1；
     * 如果签名发生变化，则说明执行方向已经改变，重复计数重新开始。</p>
     *
     * @param signature 当前步骤的执行签名
     * @return true 表示连续重复次数已经达到阈值
     */
    public boolean recordAndCheck(String signature) {
        String normalizedSignature = normalizeSignature(signature);
        if (StrUtil.isBlank(normalizedSignature)) {
            // 空签名无法用于判断，直接重置检测状态，避免误判。
            reset();
            return false;
        }
        if (normalizedSignature.equals(lastSignature)) {
            repeatCount++;
        } else {
            // 一旦签名变化，说明当前步骤已经切换到新的动作，重新计数。
            lastSignature = normalizedSignature;
            repeatCount = 1;
        }
        return repeatCount >= repeatThreshold;
    }

    /**
     * 清空检测状态。
     *
     * <p>每开启一轮新的 Agent 任务，都应该先 reset，避免上一次运行的状态污染本次判断。</p>
     */
    public void reset() {
        lastSignature = null;
        repeatCount = 0;
    }

    /**
     * 规范化签名。
     *
     * <p>这里做了两件事：
     * 1. 压缩多余空白，让格式差异不影响判断；
     * 2. 截断过长内容，避免日志和内存中保留过多无关信息。</p>
     */
    private String normalizeSignature(String signature) {
        String normalized = StrUtil.blankToDefault(signature, "")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() <= maxSignatureLength) {
            return normalized;
        }
        return normalized.substring(0, maxSignatureLength);
    }
}
