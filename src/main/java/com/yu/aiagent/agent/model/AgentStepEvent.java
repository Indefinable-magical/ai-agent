package com.yu.aiagent.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 执行过程事件。
 *
 * <p>这个对象专门用于 SSE 推送“过程可视化”信息，和最终聊天回复分开传输。
 * 前端可以根据 type 展示不同的时间线状态，比如搜索、读取文件、调用工具、生成报告。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentStepEvent {

    /**
     * 第几步，从 1 开始。
     */
    private Integer step;

    /**
     * 事件类型，比如 thinking、searching、reading_file、calling_tool、generating_report。
     */
    private String type;

    /**
     * 给用户看的简短标题。
     */
    private String title;

    /**
     * 更具体的过程描述。
     */
    private String description;

    /**
     * 当前状态：running、success、error。
     */
    private String status;

    /**
     * 事件创建时间，前端用于展示或排序。
     */
    private Long timestamp;
}
