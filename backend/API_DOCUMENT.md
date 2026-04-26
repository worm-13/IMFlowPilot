# IMFlowPilot 后端接口规范（Phase 1：WebSocket 实时聊天）

## 概述
服务名称：IMFlowPilot 后端智能体服务（Phase 1）  
通信协议：WebSocket（长连接全双工）

## 连接信息
地址：ws://localhost:8080/ws  
无子协议要求（允许跨域来源，如 http://localhost:5173）

## 消息协议（客户端 → 服务端）
- 客户端以 JSON 文本发送消息，格式参见下方 ChatMessage。
- 服务端解析后直接广播给所有在线客户端（不做 AI 处理，本阶段仅转发）。

## 消息协议（服务端 → 客户端）
- 服务端以 JSON 文本广播 ChatMessage。
- 若发生错误，服务端会发送如下格式的错误消息：
```json
{
  "id":"<uuid>",
  "sender":"system",
  "content":"internal error: message",
  "timestamp": 1710000000000
}
```

## ChatMessage 模型（示例）
```json
{
  "id": "e8b7f6c0-1d23-4a7b-9a2f-0e8a9c2d3f4b",
  "sender": "user123",
  "content": "你好，IMFlowPilot!",
  "timestamp": 1710000000000
}
```

字段说明：
- id: 消息唯一 id（客户端可生成或服务端回填）
- sender: 发送者标识（本阶段不校验）
- content: 文本内容
- timestamp: 毫秒级时间戳

## 功能要求（实现清单）
- WebSocket 端点：/ws
- 连接建立：记录日志、加入会话集合
- 接收消息：解析 JSON -> ChatMessage，记录日志 -> 广播
- 连接断开：记录日志、从会话集合移除
- 会话管理：线程安全的内存会话集合（无持久化）
- CORS：允许前端域（例如 http://localhost:5173）

## 非功能性要求
- 并发：支持多客户端并发连接（基于 Concurrent 集合）
- 清理：连接关闭时清理资源

## 错误处理
- JSON 解析失败或内部异常时，返回一条 sender 为 "system" 的错误消息给发送方并记录日志。

## 后续 TODO
- 引入 AI Agent 调用链路（Phase 2）
- 会话用户绑定与鉴权
- 消息存储与回放
