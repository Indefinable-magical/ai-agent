# AI 超级智能体项目

> 作者：[程序员小雨](https://yuyuanweb.feishu.cn/wiki/Abldw5WkjidySxkKxU2cQdAtnah)

基于 Spring AI 的智能体（Agent）项目，集成了 RAG 知识库、工具调用（Tool Calling）、MCP 协议等多种 AI 能力，并提供了股票领域的智能化应用。

## 项目架构

```
ai-agent/
├── ai-agent-frontend/        # 前端项目（Vue 3 + Vite）
├── image-search-mcp-server/  # 图片搜索 MCP 服务（独立可部署）
├── sql/                      # 数据库初始化脚本
├── src/
│   ├── main/
│   │   ├── java/com/yu/aiagent/
│   │   │   ├── agent/          # Agent 核心：BaseAgent、ReActAgent、YuManus
│   │   │   ├── app/            # 业务应用层（如 StockApp）
│   │   │   ├── controller/     # 接口层：AI 对话、股票信息、用户管理
│   │   │   ├── model/          # DTO、实体、VO、枚举
│   │   │   ├── rag/            # RAG 知识库：文档读取、检索增强、查询转换
│   │   │   ├── service/        # 业务服务层
│   │   │   ├── tools/          # 工具：文件操作、PDF 生成、网页搜索等
│   │   │   └── config/         # 配置：CORS、Json 等
│   │   └── resources/
│   │       ├── mapper/         # MyBatis 映射文件
│   │       ├── prompts/        # AI 提示词模板
│   │       └── application.yml # 主配置文件
│   └── test/                   # 单元测试
└── pom.xml                     # Maven 构建文件
```

## 技术栈

### 后端

- **Java 21** + **Spring Boot 3.4** + **Spring AI 1.0**
- **LangChain4j** — 多模型适配与链式调用
- **RAG 知识库** — 文档加载、向量检索、查询重写
- **PGvector** — 向量数据库，支持余弦相似度检索
- **Tool Calling** — 动态工具注册与调用
- **MCP**（Model Context Protocol） — 标准化模型上下文协议
- **ReAct Agent** — 推理-行动循环的智能体构建
- **MyBatis-Plus** — ORM 框架
- **MySQL + Redis + PostgreSQL** — 数据存储与缓存

### 前端

- **Vue 3** + **Vite** + **TypeScript**
- **Ant Design Vue** — UI 组件库
- **Pinia** — 状态管理
- **Vue Router** — 路由管理

### 部署与工具

- **Docker** — 容器化部署
- **Ollama** — 本地大模型部署
- **Knife4j** — 接口文档
- **Kryo** — 高性能序列化
- **Jsoup** — 网页抓取
- **iText** — PDF 生成

## 功能特性

- 🤖 **AI 超级智能体（YuManus）** — 支持多轮对话、工具调用、自主决策的 ReAct Agent
- 📈 **股票 AI 大师** — 基于 RAG 知识库的股票智能问答与分析
- 🔍 **RAG 知识库** — 支持 CSV、Markdown、PDF、网页等多种文档源
- 🛠️ **工具调用** — 文件操作、网页搜索、PDF 生成、终端操作等
- 🔗 **MCP 协议** — 通过 MCP 接入高德地图、图片搜索等第三方服务
- 💬 **对话管理** — 会话记忆持久化、上下文管理
- 🧠 **多模型支持** — 阿里云百炼 DashScope、Ollama 本地模型
- 📊 **股票工作台** — 自选股管理、风险偏好设置、报告导出

## 快速开始

### 前置要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0+
- Redis
- PostgreSQL（含 pgvector 插件）
- Node.js 18+（前端）

### 1. 数据库初始化

执行 `sql/create_table.sql` 初始化数据库表结构。

### 2. 配置

```yaml
# 复制 application.yml 配置，创建本地配置
# src/main/resources/application-local.yml
spring:
  ai:
    dashscope:
      api-key: sk-你的API密钥
  datasource:
    url: jdbc:mysql://localhost:3306/ai_code
    username: root
    password: 你的数据库密码
```

### 3. 启动后端

```bash
mvn clean package -DskipTests
java -jar target/yu-ai-agent-0.0.1-SNAPSHOT.jar
```

### 4. 启动前端

```bash
cd ai-agent-frontend
npm install
npm run dev
```

### 5. 访问

- 前端页面：http://localhost:5173
- 接口文档：http://localhost:8123/api/swagger-ui.html

## 项目配置说明

| 配置文件 | 用途 | 是否提交 |
|---------|------|---------|
| `application.yml` | 公共配置模板（占位值） | ✅ |
| `application-local.yml` | 本地开发配置（真实密钥） | ❌ 已屏蔽 |
| `application-prod.yml` | 生产环境配置 | ✅ |
| `mcp-servers.json` | MCP 服务注册配置 | ✅ |

## 开发路线

- [x] Spring AI 基础集成
- [x] RAG 知识库检索
- [x] ReAct Agent 智能体
- [x] Tool Calling 工具调用
- [x] MCP 协议集成
- [x] YuManus 超级智能体
- [ ] 更多 MCP 服务接入
- [ ] 多模态能力扩展