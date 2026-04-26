package com.worm.server.handler;

import java.util.UUID;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worm.server.model.ChatMessage;
import com.worm.server.service.SessionManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler(SessionManager sessionManager, ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionManager.addSession(session);
        logger.info("WebSocket connected: sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            ChatMessage chatMessage = objectMapper.readValue(message.getPayload(), ChatMessage.class);

            if (chatMessage.getId() == null || chatMessage.getId().isBlank()) {
                chatMessage.setId(UUID.randomUUID().toString());
            }
            if (chatMessage.getTimestamp() == null) {
                chatMessage.setTimestamp(System.currentTimeMillis());
            }

            if (!chatMessage.isValid()) {
                logger.warn("Invalid chat message from session {}: sender/content missing", session.getId());
                return;
            }

            logger.info("Incoming message: sessionId={}, sender={}, content={}",
                    session.getId(), chatMessage.getSender(), chatMessage.getContent());

            String outboundJson = objectMapper.writeValueAsString(chatMessage);
            sessionManager.broadcast(outboundJson);
        } catch (JsonProcessingException ex) {
            logger.warn("Invalid chat message payload from session {}: {}", session.getId(), message.getPayload(), ex);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.error("WebSocket transport error: sessionId={}", session.getId(), exception);
        sessionManager.removeSession(session);

        if (session.isOpen()) {
            try {
                session.close();
            } catch (IOException ex) {
                logger.warn("Failed to close session after transport error: sessionId={}", session.getId(), ex);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.removeSession(session);
        logger.info("WebSocket disconnected: sessionId={}, reason={}", session.getId(), status);
    }
}

