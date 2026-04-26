package com.imflowpilot.backend.model.vo;

/**
 * 发送给前端的消息 VO
 * 优先使用字段 content；兼容 message / text
 */
public class MessageVO {
    // ...existing code...
    private String content; // 首选
    private String message; // 兼容
    private String text;    // 兼容

    // 可扩展：status, errorCode, timestamp 等
}

