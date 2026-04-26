package com.imflowpilot.backend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imflowpilot.backend.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 处理器：连接管理、接收消息并广播
 *
 * 现在改为内部管理会话集合，避免依赖外部 SessionManager Bean 导致自动装配失败。
 */
@Component
public class WebSocketHandler extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 内部线程安全会话集合
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public WebSocketHandler() {
        // no-op
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        logger.info("WebSocket connected: sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        try {
            ChatMessage chatMessage = objectMapper.readValue(payload, ChatMessage.class);
            if (chatMessage.getId() == null || chatMessage.getId().isEmpty()) {
                chatMessage.setId(UUID.randomUUID().toString());
            }
            if (chatMessage.getTimestamp() == null) {
                chatMessage.setTimestamp(System.currentTimeMillis());
            }
            logger.info("Message received from session {}: {}", session.getId(), chatMessage.getContent());

            // 广播给所有在线客户端（包含 sender 自己）
            String out = objectMapper.writeValueAsString(chatMessage);
            broadcast(out);

        } catch (Exception ex) {
            logger.error("Failed to handle message from session {}: {}", session.getId(), ex.getMessage());
            try {
                ChatMessage err = new ChatMessage();
                err.setId(UUID.randomUUID().toString());
                err.setSender("system");
                err.setContent("invalid message or internal error");
                err.setTimestamp(System.currentTimeMillis());
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(err)));
            } catch (Exception ignore) { }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        sessions.remove(session);
        logger.info("WebSocket disconnected: sessionId={} status={}", session.getId(), status);
    }

    // 广播实现
    private void broadcast(String message) {
        TextMessage text = new TextMessage(message);
        for (WebSocketSession s : sessions) {
            if (s == null) continue;
            try {
                if (s.isOpen()) {
                    s.sendMessage(text);
                }
            } catch (IOException e) {
                logger.warn("Failed to send message to session {}: {}", s != null ? s.getId() : "null", e.getMessage());
            }
        }
    }
}
