# AI Agent 智能停车平台

基于 **LangGraph 多智能体 + MCP 协议 + RAG 知识检索** 的智能停车管理平台，覆盖"找车位 → 预约 → 到场 → 停车 → 结算 → 支付"完整业务闭环。

## ✨ 核心特性

| 特性 | 说明 |
|---|---|
| 🤖 **多智能体协作** | LangGraph 5 节点状态机，意图路由 + 推荐/客服/分析 3 个专业 Agent |
| 🔌 **MCP 协议** | 标准化暴露 Tools/Resources/Prompts，Claude Desktop 等任意 MCP 客户端可直接调用 |
| 📚 **RAG 知识检索** | Qdrant 向量检索 + 本地中文 Embedding，21 条评估集量化 hit-rate |
| 💰 **完整业务闭环** | 预约 → 到场开单 → 离场结算 → 支付，计费引擎 + 订单状态机 |
| 🚗 **无感推荐** | 车辆档案自动补全需求条件，一句话"帮我停车"即可完成推荐 |
| 📈 **需求预测** | 「星期几×小时」分组移动平均 + 置信度评估 + 冷启动策略 |
| ⚡ **SSE 流式响应** | 实时推送 Agent 执行过程（节点流转/工具调用/token 生成） |
| 🔒 **内部 API 鉴权** | X-Internal-Key 密钥校验，密钥不出服务端 |
| 🐳 **一键部署** | Docker Compose 编排 MySQL + Redis + Java + Agent |

## 🏗️ 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        用户入口层                                 │
│                                                                  │
│   ┌──────────┐   ┌──────────────┐   ┌────────────┐              │
│   │ Chat UI  │   │  管理后台    │   │ MCP Client │              │
│   │ (Vue 3)  │   │  数据看板    │   │(Claude等)  │              │
│   └────┬─────┘   └──────┬───────┘   └─────┬──────┘              │
└────────┼────────────────┼─────────────────┼──────────────────────┘
         │ SSE / HTTP     │                 │ MCP 协议
         ▼                ▼                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                   智能交互层 (Python FastAPI :8001)               │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              LangGraph 多智能体状态机                      │   │
│  │                                                          │   │
│  │   用户消息 → [Router] ─┬→ [Recommend Agent] → END        │   │
│  │              意图分类   ├→ [Service Agent]   → END        │   │
│  │              规则+LLM   ├→ [Analytics Agent] → END        │   │
│  │                        └→ [Chitchat]        → END        │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │ RAG 检索器   │  │ 会话记忆     │  │ Tool Pool (14 个)    │   │
│  │ (Qdrant)     │  │ (内存/Redis) │  │ httpx → Java 内部API │   │
│  └──────────────┘  └──────────────┘  └──────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  MCP Server (Tools / Resources / Prompts)                │   │
│  └──────────────────────────────────────────────────────────┘   │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTP (X-Internal-Key)
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   业务引擎层 (Java Spring Boot :8080)             │
│                                                                  │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐             │
│  │ 四层推荐流水线│ │  订单服务    │ │  预约服务    │             │
│  │ NLU→规则过滤 │ │  计费引擎    │ │  乐观锁      │             │
│  │ →加权排序    │ │  结算/支付   │ │  两阶段预约  │             │
│  │ →AI 解释     │ │              │ │              │             │
│  └──────────────┘ └──────────────┘ └──────────────┘             │
│                                                                  │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐             │
│  │ 车辆档案服务 │ │ 需求预测服务 │ │  统计分析    │             │
│  │ 无感推荐补全 │ │ 移动平均模型 │ │  占用率/收入 │             │
│  └──────────────┘ └──────────────┘ └──────────────┘             │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                         数据层                                    │
│   ┌──────────┐  ┌──────────┐  ┌──────────────┐                  │
│   │  MySQL   │  │  Redis   │  │   Qdrant     │                  │
│   │ 业务数据  │  │ 会话缓存 │  │  向量存储    │                  │
│   └──────────┘  └──────────┘  └──────────────┘                  │
└─────────────────────────────────────────────────────────────────┘
```

## 🛠️ 技术栈

| 层次 | 技术 |
|---|---|
| **Agent 框架** | Python, LangChain 0.3, LangGraph 0.6, FastAPI |
| **AI/LLM** | mimo-v2.5-pro (OpenAI 兼容), fastembed 本地中文 Embedding |
| **向量数据库** | Qdrant |
| **后端引擎** | Java 17, Spring Boot 3.3, MyBatis 3.0 |
| **数据库** | MySQL 8.0, Redis 7 |
| **协议** | MCP (Model Context Protocol), SSE (Server-Sent Events) |
| **前端** | Vue 3 (CDN), Chat UI |
| **可观测性** | Langfuse (LLM 调用链路追踪) |
| **部署** | Docker Compose |
| **测试** | JUnit 5 (149), pytest (41), 共 190 个用例 |

## 📦 项目结构

```
parking-recommendation/
├── src/main/java/.../                  # Java 业务引擎
│   ├── controller/
│   │   ├── DriverController.java       # 面向前端的司机 API
│   │   ├── AdminController.java        # 管理端 API
│   │   └── InternalController.java     # 内部 API（供 Agent 调用）
│   ├── core/
│   │   ├── nlu/                        # NLU 解析（LLM）
│   │   ├── rule/                       # 硬条件过滤 + 软偏好排序（确定性）
│   │   ├── recommender/                # 加权评分
│   │   ├── explainer/                  # AI 推荐解释
│   │   ├── billing/                    # 计费引擎（纯逻辑）
│   │   └── prediction/                 # 占用率预测器（纯逻辑）
│   ├── service/                        # 业务服务层
│   ├── mapper/                         # MyBatis Mapper
│   └── config/                         # 调度器、CORS、鉴权过滤器
├── agent-service/                      # Python Agent 服务
│   ├── main.py                         # FastAPI 入口（SSE 流式）
│   ├── graph/                          # LangGraph 状态机
│   │   ├── builder.py                  # 图构建（条件边路由）
│   │   └── nodes/                      # Router / Recommend / Service / Analytics
│   ├── tools/                          # 14 个工具（调 Java 内部 API）
│   ├── rag/                            # RAG 管线（Embedding + Qdrant）
│   ├── memory/                         # 会话记忆（内存/Redis 双模式）
│   ├── prompts/                        # 各 Agent 的 System Prompt
│   └── eval/                           # RAG 检索评估（21 条问题）
├── mcp-server/                         # MCP Server
│   ├── server.py                       # Tools / Resources / Prompts
│   └── claude_desktop_config_example.json
├── docs/                               # RAG 知识文档
│   ├── parking_rules.txt               # 停车场规则
│   ├── faq.txt                         # 常见问题
│   └── spot_features.txt               # 区域特色
├── docker-compose.yml                  # 一键部署
├── Dockerfile.java                     # Java 多阶段构建
└── src/main/resources/
    ├── schema.sql                      # 建库建表 + 种子数据
    ├── migration_v2.sql                # V2 增量迁移
    └── static/index.html               # Chat UI 前端
```

## 🚀 快速启动

### 环境要求

- Java 17 + Maven 3.8+
- Python 3.10+ (推荐 Anaconda)
- MySQL 8.0
- Qdrant (本地或远程)

### 1. 初始化数据库

```bash
mysql -u root -p < src/main/resources/schema.sql
# 已有数据库执行增量迁移：
# mysql -u parking -p parking_recommendation < src/main/resources/migration_v2.sql
```

### 2. 启动 Java 业务引擎

```bash
mvn spring-boot:run
# 默认端口 8080，验证：
curl http://localhost:8080/api/admin/spots
```

### 3. 启动 Python Agent 服务

```bash
cd agent-service
conda activate aiNative   # 或你的 Python 环境
pip install -r requirements.txt
python main.py
# 端口 8001，验证：
curl http://localhost:8001/agent/health
```

### 4. 打开前端

浏览器访问 **http://localhost:8080**，使用种子用户登录：

| 用户名 | 密码 | 角色 |
|---|---|---|
| `u001` | `123456` | 司机 |
| `admin` | `admin123` | 管理员 |

### Docker 一键部署（可选）

```bash
docker compose up -d
# 自动启动 MySQL(含种子数据) + Redis + Java + Agent
# Qdrant 默认使用远程实例，如需本地取消 compose 中注释
```

## 💬 使用示例

### 对话式找车位

```
👤 帮我停车
🤖 根据您的车辆信息（特斯拉 Model 3，新能源，车高1.44米），为您推荐：
   🥇 S001 — 限高2.5米 | 有充电桩 | 距电梯10米 | 10元/小时
   🥈 S012 — 限高2.4米 | 有充电桩 | 距电梯12米 | 11元/小时

👤 帮我预约第一个
🤖 预约成功！预约号 R1A2B3C4，车位 S001，1小时内有效。

👤 我到了
🤖 已确认到场，停车订单已开立，计时开始。

👤 我要走了，帮我结算
🤖 结算完成！停车费 ¥15.00，充电费 ¥24.00，总金额 ¥39.00。确认支付吗？

👤 支付
🤖 支付成功！感谢您的使用，欢迎下次光临！
```

### 其他能力

```
👤 柴油车能停B区吗？           → RAG 知识库检索回答
👤 晚上7点去有位置吗？         → 需求预测 + 错峰建议
👤 现在停车场占用率多少？      → 实时统计
```

## 🔌 MCP 集成

将本项目的停车服务能力暴露给任意 MCP 客户端（Claude Desktop、Cursor 等）：

```json
// claude_desktop_config.json
{
  "mcpServers": {
    "parking-assistant": {
      "command": "python",
      "args": ["path/to/mcp-server/server.py"],
      "env": {
        "JAVA_BACKEND_URL": "http://localhost:8080",
        "INTERNAL_API_KEY": "parking-internal-key-2024"
      }
    }
  }
}
```

MCP 暴露的能力：
- **Tools**: `recommend_parking`, `reserve_parking`, `cancel_reservation`, `confirm_arrival`, `query_available_spots`
- **Resources**: `parking://spots/available`, `parking://rules`
- **Prompts**: `find-parking`, `check-my-reservations`

## 🧪 测试

```bash
# Java 测试 (149 个用例)
mvn test

# Python 测试 (41 个用例)
cd agent-service
python -m pytest tests/ -v

# RAG 检索评估 (21 条问题，hit-rate@3)
python -m eval.evaluate
```

## 📊 业务流程

```
┌────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌────────┐
│ 找车位  │───→│ 预约     │───→│ 确认到场  │───→│ 离场结算  │───→│ 支付   │
│(推荐)  │    │(pending) │    │(开单计时) │    │(计费)    │    │(paid)  │
└────────┘    └──────────┘    └──────────┘    └──────────┘    └────────┘
                  │ 1h TTL                        │
                  └── 超时自动取消 ──→ available  └── 释放车位 → available
```

**订单状态机**: `parking` → `completed` → `paid`
**计费规则**: 停车费 = ceil(时长/1h) × 单价，充电费 = 度数 × 1.2 元

## 📈 需求预测

- **模型**: 按「星期几 × 小时」二维分组的移动平均
- **置信度**: min(同组样本数 / 10, 1.0)，样本不足退化为全量均值
- **冷启动**: 自动生成 14 天合成历史数据（工作日/周末双曲线 + 噪声）
- **评估**: `python -m eval.evaluate` 输出 hit-rate@3

## 🔧 配置说明

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `LLM_BASE_URL` | `https://api.xiaomimimo.com/v1` | LLM API 地址（OpenAI 兼容） |
| `LLM_API_KEY` | - | LLM API Key |
| `JAVA_BACKEND_URL` | `http://localhost:8080` | Java 后端地址 |
| `QDRANT_HOST` | `192.168.100.128` | Qdrant 地址 |
| `INTERNAL_API_KEY` | `parking-internal-key-2024` | 内部 API 密钥 |
| `REDIS_URL` | - | Redis 地址（留空用内存） |
| `LANGFUSE_*` | - | Langfuse 可观测性（三项齐全启用） |
| `HF_ENDPOINT` | `https://hf-mirror.com` | HuggingFace 镜像（国内必需） |


