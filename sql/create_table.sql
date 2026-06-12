# 数据库初始化

-- 创建库
create database if not exists ai_code;

-- 切换库
use ai_code;

-- 用户表
-- 以下是建表语句

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    userStatus   tinyint      default 0                 not null comment '用户状态：0 正常，1 禁用',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 如果是已有 user 表，需要单独执行下面的迁移语句补充账号状态字段：
ALTER TABLE user ADD COLUMN userStatus tinyint default 0 not null comment '用户状态：0 正常，1 禁用' AFTER userRole;

-- 用户通用偏好表
-- 主题、默认 AI 应用、默认风险偏好和对话密度都放在这里，保证换设备登录后体验一致
create table if not exists user_preference
(
    id                    bigint auto_increment comment 'id' primary key,
    userId                bigint                                not null comment '用户 id',
    theme                 varchar(32) default 'cyber'           not null comment '界面主题：cyber/light',
    defaultAiApp          varchar(32) default 'home'            not null comment '默认 AI 应用：home/stock-master/super-agent',
    defaultRiskPreference varchar(32) default 'balanced'        not null comment '默认风险偏好：conservative/balanced/aggressive',
    conversationDensity   varchar(32) default 'comfortable'     not null comment '对话密度：compact/comfortable',
    createTime            datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime            datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete              tinyint     default 0                 not null comment '是否删除',
    UNIQUE KEY uk_user_preference (userId, isDelete)
) comment '用户通用偏好' collate = utf8mb4_unicode_ci;

-- 对话记忆表
-- 用于后续自定义 ChatMemory，把 AI 股票大师、AI 超级智能体等应用的多轮对话持久化到 MySQL
create table if not exists chat_memory
(
    id             bigint auto_increment comment 'id' primary key,
    conversationId varchar(128)                          not null comment '会话 id',
    userId         bigint                                null comment '用户 id，未登录或系统会话可为空',
    appType        varchar(64)                           not null comment '应用类型：stock_app/manus 等',
    messageType    varchar(32)                           not null comment '消息类型：USER/ASSISTANT/SYSTEM/TOOL',
    content        mediumtext                            null comment '消息正文',
    metadata       json                                  null comment '消息元数据，保存模型、工具调用等扩展信息',
    messageOrder   int         default 0                 not null comment '消息在会话中的顺序',
    createTime     datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime     datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete       tinyint     default 0                 not null comment '是否删除',
    INDEX idx_conversationId_messageOrder (conversationId, messageOrder),
    INDEX idx_userId_appType (userId, appType),
    INDEX idx_createTime (createTime)
) comment '对话记忆' collate = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stock_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'id',
    stockCode VARCHAR(32) NOT NULL COMMENT '股票代码',
    stockName VARCHAR(64) NOT NULL COMMENT '股票名称',
    market VARCHAR(32) DEFAULT 'A_SHARE' COMMENT '市场',
    industry VARCHAR(128) DEFAULT NULL COMMENT '所属行业',
    themes VARCHAR(512) DEFAULT NULL COMMENT '主题标签',
    riskTags VARCHAR(512) DEFAULT NULL COMMENT '风险标签',
    reason TEXT COMMENT '入池理由',
    hotScore INT DEFAULT 0 COMMENT '热度分',
    createTime DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updateTime DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    KEY idx_stock_code (stockCode),
    KEY idx_stock_name (stockName),
    KEY idx_industry (industry),
    KEY idx_hot_score (hotScore),
    KEY idx_is_delete (isDelete)
) COMMENT='股票结构化信息';

CREATE TABLE IF NOT EXISTS stock_watchlist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'id',
    userId BIGINT NOT NULL COMMENT '用户 id',
    stockCode VARCHAR(32) NOT NULL COMMENT '股票代码',
    stockName VARCHAR(64) NOT NULL COMMENT '股票名称',
    remark VARCHAR(256) DEFAULT NULL COMMENT '关注理由',
    createTime DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updateTime DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    KEY idx_user_stock (userId, stockCode, isDelete),
    KEY idx_user_update_time (userId, updateTime)
) COMMENT='用户自选股列表';

CREATE TABLE IF NOT EXISTS stock_user_preference (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'id',
    userId BIGINT NOT NULL COMMENT '用户 id',
    riskPreference VARCHAR(32) DEFAULT 'balanced' COMMENT '风险偏好：conservative 稳健 / balanced 平衡 / aggressive 激进',
    createTime DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updateTime DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    KEY idx_user_preference (userId, isDelete)
) COMMENT='股票用户偏好';

INSERT INTO stock_info
(stockCode, stockName, market, industry, themes, riskTags, reason, hotScore, isDelete)
VALUES
    ('300394', '天孚通信', 'A股', '光通信', 'AI算力;光模块;CPO', '估值波动;海外需求变化', 'AI数据中心高速光模块核心方向之一', 100, 0);
