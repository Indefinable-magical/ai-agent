package com.yu.aiagent.model.cache;

import lombok.Data;

/**
 * 内存登录失败记录。
 *
 * <p>failureCount 记录连续失败次数，lockUntil 记录锁定截止时间戳。
 * 如果后续部署多实例，可以把这部分数据迁移到 Redis。</p>
 */
@Data
public class LoginFailureRecord {

    /**
     * 连续登录失败次数。
     */
    private int failureCount;

    /**
     * 锁定截止时间戳，0 表示未锁定。
     */
    private long lockUntil;
}
