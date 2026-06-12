package com.yu.aiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yu.aiagent.model.entity.StockInfo;

/**
 * 股票结构化信息 Mapper。
 *
 * <p>继承 MyBatis-Plus 的 BaseMapper 后，就自动拥有 insert、delete、update、
 * selectById、selectList 等基础 CRUD 能力。当前混合检索主要使用 selectList
 * 根据股票名称、行业、主题、风险标签等字段做结构化查询。</p>
 */
public interface StockInfoMapper extends BaseMapper<StockInfo> {
}
