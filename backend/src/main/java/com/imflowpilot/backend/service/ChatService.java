package com.imflowpilot.backend.service;

import com.imflowpilot.backend.model.dto.MessageDTO;
import com.imflowpilot.backend.model.vo.MessageVO;

/**
 * 聊天业务接口：处理用户消息、会话上下文与 AI 调用
 */
public interface ChatService {
    // 处理输入消息，返回用于发送给客户端的封装结果
    MessageVO handleMessage(MessageDTO message);
}

