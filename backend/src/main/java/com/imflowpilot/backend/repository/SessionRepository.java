package com.imflowpilot.backend.repository;

import com.imflowpilot.backend.model.entity.Session;

/**
 * 会话仓库占位：负责会话的 CRUD（可实现为内存映射或数据库持久化）
 */
public interface SessionRepository {
    // ...existing code...
    void save(Session session);
    Session findById(String sessionId);
    void remove(String sessionId);
}

