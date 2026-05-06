# IMFlowPilot Agent 运作文档

## 一、系统总览

Agent 是一个基于 **LangChain + DeepSeek LLM + FastAPI** 的智能助手，嵌入在 IMFlowPilot 群聊协作系统中。它通过 **两条 Prompt** 驱动两条独立的推理链路，三者通过 **Java WebSocket 后端** 串联成完整的自动化工作流。

```
┌──────────────┐     WS/HTTP      ┌──────────────┐      HTTP       ┌──────────────┐
│  Vue 前端     │ ◄──────────────► │  Java 后端    │ ◄─────────────► │  Python Agent │
│  (WebSocket)  │                  │  (Spring Boot)│                 │  (FastAPI)    │
└──────────────┘                  └──────┬───────┘                 └──────┬───────┘
                                        │                                 │
                                        │ ┌──────────────────────────────┤
                                        │ │ Redis (上下文记忆)             │
                                        │ └──────────────────────────────┘
```

---

## 二、项目结构

```
agent/
├── main.py                          # FastAPI 入口，注册 4 个 API 端点
├── config/
│   ├── llm.py                       # DeepSeek LLM 配置
│   └── redis.py                     # Redis 连接 + 历史记录读写
├── model/
│   ├── response.py                  # AgentResponse 数据模型（含 meta 字段）
│   └── plan.py                      # Plan / PlanStep 数据模型
├── prompt/
│   ├── agent_prompt.py             # ★ 意图分类系统提示词
│   └── planner_prompt.py           # ★ 任务拆解系统提示词
└── service/
    ├── agent_service.py             # 意图分类服务
    ├── planner.py                   # 任务规划服务
    └── orchestrator.py             # 任务编排引擎
```

---

## 三、四条 API 端点

| 端点 | 方法 | 调用方 | 作用 |
|:-----|:----:|:------:|------|
| `/agent/process` | POST | Java 后端 | 意图分类：返回 type + content + meta |
| `/agent/plan` | POST | Java 后端 | 任务规划：返回步骤列表 |
| `/agent/execute` | POST | Java 后端 | 任务执行：逐步执行并回调进度 |
| `/health` | GET | 外部监控 | 健康检查 |

---

## 四、两个核心 Prompt

### 4.1 意图分类 Prompt（`agent_prompt.py`）

**文件位置：** `agent/prompt/agent_prompt.py`

**作用：** 决定 Agent 对每条消息的响应类型。

**两段式设计：**

| 变量 | 内容 | 用途 |
|:-----|------|------|
| `SYSTEM_PROMPT` | 角色定义 + @ 规则 + 四分类法 + 输出格式 + meta 字段规则 + 上下文规则 + 兜底 | LangChain `SystemMessage`，注入 LLM 作为系统指令 |
| `HUMAN_TEMPLATE` | `{mentions}` + `{history}` + `{message}` | LangChain `HumanMessage`，运行时动态填充 |

**SYSTEM_PROMPT 核心规则（按优先级排序）：**

1. **默认建议规则（最高优先级）**：`DEFAULT TO "suggestion"` —— 不主动输出 `task`，只有用户明确说"开始/执行/生成/确认/好的/可以/去做吧"才输出 `task`
2. **@ 规则（次优先级）**：`mentions` 包含谁决定分类方向
3. **四分类法**：`ignore` / `mention` / `suggestion` / `task`
4. **上下文规则**：如果对话历史中有 pending task，用户说"开始"→输出 task
5. **meta 字段规则**：`suggestion` 必须带 `requires_confirmation: true`，`task` 带 `confidence: 1.0`

**LLM 输出的 JSON 格式：**

```json
{
  "type": "ignore | mention | suggestion | task",
  "content": "...",
  "meta": {
    "requires_confirmation": true,
    "suggested_task": "generate_ppt",
    "confidence": 0.85
  }
}
```

**关键约束：**
- 所有 JSON 示例用双花括号 `{{...}}` 写，防止 LangChain 模板引擎误解析为变量
- `ignore` 和 `mention` 的 `content` 必须为空字符串
- `suggestion` → `requires_confirmation = true`
- `task` → `requires_confirmation = false`

---

### 4.2 任务拆解 Prompt（`planner_prompt.py`）

**文件位置：** `agent/prompt/planner_prompt.py`

**作用：** 将高层的 task 类型（如 `generate_ppt`）拆解为 3~6 个可执行步骤。

**两段式设计：**

| 变量 | 内容 | 用途 |
|:-----|------|------|
| `PLANNER_SYSTEM_PROMPT` | 角色定义 + 上下文感知规则 + 任务类型指导 + 输出格式 + 约束 + 兜底 | 注入 LLM 作为系统指令 |
| `PLANNER_HUMAN_TEMPLATE` | `{message}` + `{task}` | 运行时动态填充 |

**TASK TYPES 指导规则：**

```
- generate_ppt: → analyze topic → outline → content → build slides
- generate_doc: → analyze → structure → write → refine
- summarize_meeting: → extract → organize → summarize → output
- analyze_data: → understand → process → analyze → report
```

**LLM 输出格式：** JSON 数组，每项含 `step`（snake_case ID）和 `name`（可读描述）。

**调用条件：** 只有当 task **不在预定义映射表 `TASK_PLANS`** 中时，才走 LLM 动态拆解。预定义的 4 种 task 直接从映射表返回，不调用 LLM。

---

## 五、完整数据流

### 阶段一：意图分类

```
用户输入 "我们要做AI汇报"
  │
  ▼
ChatWebSocketHandler.handleTextMessage()
  │
  ├─ 解析消息 → 广播原始消息到所有客户端
  ├─ 检查 mentions / confirmTask → 决定是否走确认机制
  ├─ 调用 agentClient.process(message, sessionId, mentions)
  │     │
  │     ▼
  │   POST /agent/process → AgentService.process()
  │     │
  │     ├─ 从 Redis 加载历史（_load_history）
  │     ├─ 拼接 History + Mentions + Message → 注入 HUMAN_TEMPLATE
  │     ├─ 发送 SYSTEM_PROMPT + HUMAN_TEMPLATE → DeepSeek LLM
  │     ├─ 解析 LLM 返回的 JSON
  │     ├─ 保存到 Redis（_save_history）
  │     └─ 返回 AgentResponse{type, content, meta}
  │
  ▼
handleAgentResponse()
  │
  ├─ suggestion → context.pendingTask = meta.suggestedTask
  │     → 广播给前端（附 confirmTask 字段，前端显示 [开始执行] 按钮）
  │
  ├─ task → context.activeTask = content
  │     → triggerTaskExecution()
  │
  └─ ignore/mention → 不回复
```

### 阶段二：任务确认

```
用户点击 [开始执行] 或输入 "开始"
  │
  ▼
ChatWebSocketHandler.handleTextMessage()
  ├─ confirmTask != null → handleExplicitConfirm()
  │     └─ triggerTaskExecution(taskType)
  │
  └─ isConfirmMessage("开始") && context.pendingTask != null
        └─ handleImplicitConfirm(pendingTask)
              └─ triggerTaskExecution(taskType)
```

### 阶段三：任务规划

```
triggerTaskExecution(taskType)
  │
  ▼
agentClient.plan(taskType, message, sessionId)
  │
  ▼
POST /agent/plan → PlannerService.plan()
  │
  ├─ task 在 TASK_PLANS 中？ → 直接返回预定义步骤
  └─ 否 → PLANNER_SYSTEM_PROMPT + HUMAN_TEMPLATE → DeepSeek LLM → 返回步骤
  │
  ▼
handlePlanResponse()
  ├─ 构造 PlanProgress 消息（id = "progress-{task}"，确保原地更新）
  ├─ 广播给前端（前端渲染进度卡片 / 可折叠）
  └─ 调用 agentClient.execute(plan)
```

### 阶段四：任务执行

```
agentClient.execute(plan, sessionId)
  │
  ▼
POST /agent/execute → OrchestratorService.execute()
  │
  for each step in plan.steps:
    ├─ step.status = "running" → HTTP POST callback_url (ProgressController)
    │     → ProgressController: 构造 ChatMessage (固定 ID "progress-{task}")
    │     → sessionManager.broadcast() → 前端更新进度卡片
    │
    ├─ 执行步骤（当前为 1.5s 模拟，后续可对接 DocGenerator/PptGenerator）
    │
    └─ step.status = "completed" → 同上通知
  │
  ▼
全部完成 → Java 广播 "任务已完成"
```

---

## 六、关键机制详解

### 6.1 确认机制

**两条路径防止误触发执行：**

| 方式 | 触发条件 | 说明 |
|:----:|:--------|------|
| **显式确认** | 前端发送 `confirmTask="generate_ppt"` | 用户点击 [开始执行] 按钮 |
| **隐式确认** | 用户输入"开始/好的/确认/执行" + context.pendingTask 存在 | 关键词匹配兜底 |

```
确认关键词: "开始" / "执行" / "生成" / "确认" / "好的" / "可以" / "去做吧" / "go" / "run" / "yes" / "ok"
```

### 6.2 上下文记忆（SessionContext）

Java 侧使用 `ConcurrentHashMap<String, SessionContext>` 做内存存储：

```
SessionContext {
    sessionId: "xxx",
    activeTask: "generate_ppt",     // 当前执行中的任务
    pendingTask: "generate_doc",    // 等待用户确认的任务
    history: ["消息1", "消息2", ...]  // 最近 10 条消息
}
```

Python 侧使用 Redis（`langchain_community.RedisChatMessageHistory`）做持久化存储，保留最近 5 轮对话（10 条消息）。

### 6.3 进度消息原地更新

```
Plan 消息:    id = "progress-generate_ppt"    ← 固定 ID
Progress #1:  id = "progress-generate_ppt"    ← 同一 ID，前端 upsert
Progress #2:  id = "progress-generate_ppt"    ← 同一 ID，前端 upsert
Progress #3:  id = "progress-generate_ppt"    ← 同一 ID，前端 upsert
...
Complete:     id = "progress-generate_ppt"    ← 同一 ID，最后更新为"任务已完成"
```

前端 `ChatWindow.onMounted()` 中检测到 `agentType === 'progress' || 'plan'` 时，调用 `chatStore.upsertMessage()` 而非 `addMessage()`，实现同 ID 原地替换。

### 6.4 Planner 预定义映射

```python
TASK_PLANS = {
    "generate_ppt":      5 步 (分析→大纲→内容→构建→通知),
    "generate_doc":      5 步 (分析→大纲→内容→格式化→通知),
    "analyze_data":      4 步 (解析→查询→分析→通知),
    "modify_ppt":        3 步 (解析→修改→通知),
}
```

未知 task 走 LLM 动态拆解（`planner_prompt.py`），失败则兜底为单步骤"处理请求"。

---

## 七、数据模型

### AgentResponse（`agent/model/response.py`）

| 字段 | 类型 | 说明 |
|:-----|:----:|------|
| `type` | `str` | `ignore` / `mention` / `suggestion` / `task` |
| `content` | `str` | 回复内容或 task ID |
| `requires_confirmation` | `bool` | 是否需要用户确认 |
| `suggested_task` | `str` | 建议的任务类型 |
| `confidence` | `float` | 置信度 0.0~1.0 |

### Plan / PlanStep（`agent/model/plan.py`）

| 字段 | 类型 | 说明 |
|:-----|:----:|------|
| `step` | `str` | snake_case 步骤 ID |
| `name` | `str` | 可读步骤描述 |
| `status` | `str` | `pending` / `running` / `completed` / `failed` |

---

## 八、API 请求/响应示例

### POST /agent/process

**请求：**
```json
{
  "message": "帮我生成PPT",
  "session_id": "abc123",
  "mentions": ["agent"]
}
```

**响应：**
```json
{
  "type": "suggestion",
  "content": "好的，建议先确认一下PPT的主题，你有什么具体要求吗？",
  "meta": {
    "requires_confirmation": true,
    "suggested_task": "generate_ppt",
    "confidence": 0.8
  }
}
```

### POST /agent/plan

**请求：**
```json
{
  "task": "generate_ppt",
  "message": "帮我生成PPT",
  "session_id": "abc123"
}
```

**响应：**
```json
{
  "task": "generate_ppt",
  "message": "帮我生成PPT",
  "steps": [
    {"step": "analyze_requirements", "name": "分析需求与主题", "status": "pending"},
    {"step": "generate_outline", "name": "生成PPT大纲", "status": "pending"},
    {"step": "generate_slides", "name": "生成每页内容", "status": "pending"},
    {"step": "build_ppt", "name": "构建PPT文件", "status": "pending"},
    {"step": "notify_user", "name": "通知用户完成", "status": "pending"}
  ]
}
```

### POST /agent/execute

**请求：**
```json
{
  "task": "generate_ppt",
  "message": "帮我生成PPT",
  "steps": [{"step": "analyze", "name": "分析", "status": "pending"}, ...],
  "callback_url": "http://localhost:8080/api/agent/progress"
}
```

**响应：** 与 `/agent/plan` 相同，但 steps 状态已全部更新为 `completed`/`failed`。

---

## 九、配置项

| 配置 | 环境变量 | 默认值 | 说明 |
|:-----|:--------:|:------:|------|
| Redis URL | `REDIS_URL` | `redis://localhost:6379` | 上下文记忆存储 |
| Session TTL | `AGENT_SESSION_TTL` | `3600` | 会话过期秒数 |
| 最大历史轮次 | `AGENT_MAX_HISTORY` | `5` | 保留最近 N 轮对话 |
| Agent 基地址 | `agent.base-url` | `http://localhost:8000` | Java 后端配置 |

---

## 十、依赖技术栈

| 层 | 技术 | 用途 |
|:--:|:----|------|
| Agent | LangChain + ChatPromptTemplate | Prompt 管理 + LLM 调用链 |
| Agent | DeepSeek Chat API | 大语言模型推理 |
| Agent | FastAPI + Uvicorn | HTTP API 服务 |
| Agent | LangChain RedisChatMessageHistory | 上下文记忆存储 |
| 后端 | Spring Boot 4.0 + WebSocket | 消息中转 + 任务调度 |
| 后端 | WebClient | 异步 HTTP 调用 Agent |
| 前端 | Vue 3 + Pinia + Vite | 聊天 UI + 状态管理 |
