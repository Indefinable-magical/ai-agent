package com.yu.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 股票结构化信息实体。
 *
 * <p>对应 MySQL 表 stock_info，用于混合检索中的结构化数据查询。
 * 它和 Markdown/CSV/PDF 等非结构化知识库互补：非结构化知识适合语义检索，
 * 结构化表更适合按照股票代码、股票名称、行业、主题、风险标签精确查询。</p>
 */
@TableName(value = "stock_info")
@Data
public class StockInfo {

    /**
     * 主键 id。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 股票代码，例如 300308。
     */
    private String stockCode;

    /**
     * 股票名称，例如 中际旭创。
     */
    private String stockName;

    /**
     * 所属市场，例如 A股、港股、美股。
     */
    private String market;

    /**
     * 所属行业，例如 光通信、半导体、电力设备。
     */
    private String industry;

    /**
     * 主题标签，多个标签可以用逗号或分号分隔。
     */
    private String themes;

    /**
     * 风险标签，多个标签可以用逗号或分号分隔。
     */
    private String riskTags;

    /**
     * 关注理由，用于告诉 AI 为什么这个股票和某个主题相关。
     */
    private String reason;

    /**
     * 热度分，数值越高越优先展示。
     */
    private Integer hotScore;

    /**
     * 创建时间。
     */
    private Date createTime;

    /**
     * 更新时间。
     */
    private Date updateTime;

    /**
     * 逻辑删除字段，0 表示正常，1 表示删除。
     */
    @TableLogic
    private Integer isDelete;
}
