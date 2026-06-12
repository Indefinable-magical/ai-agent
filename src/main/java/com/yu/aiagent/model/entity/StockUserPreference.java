package com.yu.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 股票大师用户偏好。
 *
 * <p>当前先保存风险偏好，后续可以继续扩展默认分析周期、展示密度等个性化配置。</p>
 */
@TableName(value = "stock_user_preference")
@Data
public class StockUserPreference {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户 id
     */
    private Long userId;

    /**
     * 风险偏好：conservative / balanced / aggressive
     */
    private String riskPreference;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Integer isDelete;
}
