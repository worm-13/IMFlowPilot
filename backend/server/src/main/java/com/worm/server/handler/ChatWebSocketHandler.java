package com.worm.server.handler;

import java.util.UUID;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worm.server.client.AgentClient;
import com.worm.server.dto.AgentResponse;
import com.worm.server.dto.PlanResponse;
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
    private final AgentClient agentClient;

    private final ConcurrentHashMap<String, String> agentSessionMap = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(SessionManager sessionManager, ObjectMapper objectMapper,
            AgentClient agentClient) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
        this.agentClient = agentClient;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionManager.addSession(session);
        String agentSessionId = UUID.randomUUID().toString();
        agentSessionMap.put(session.getId(), agentSessionId);
        logger.info("WebSocket connected: sessionId={}, agentSessionId={}", session.getId(), agentSessionId);
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

            logger.info("Incoming message: sessionId={}, sender={}, content={}, mentions={}",
                    session.getId(), chatMessage.getSender(), chatMessage.getContent(), chatMessage.getMentions());

            String outboundJson = objectMapper.writeValueAsString(chatMessage);
            sessionManager.broadcast(outboundJson);

            String agentSessionId = agentSessionMap.get(session.getId());

            List<String> mentions = chatMessage.getMentions();
            boolean shouldCallAgent = (mentions == null || mentions.isEmpty() || mentions.contains("agent"));

            if (shouldCallAgent) {
                agentClient.process(chatMessage.getContent(), agentSessionId, mentions)
                        .thenAccept(response -> handleAgentResponse(response, chatMessage, agentSessionId))
                        .exceptionally(ex -> {
                            logger.error("Agent async processing failed", ex);
                            return null;
                        });
            } else {
                logger.info("Message directed to others (mentions={}), skipping agent", mentions);
            }
        } catch (JsonProcessingException ex) {
            logger.warn("Invalid chat message payload from session {}: {}", session.getId(), message.getPayload(), ex);
        }
    }

    private void handleAgentResponse(AgentResponse response, ChatMessage originalMessage, String agentSessionId) {
        if (response == null || response.isIgnore() || response.isMention()) {
            return;
        }

        ChatMessage agentMessage = new ChatMessage();
        agentMessage.setId(UUID.randomUUID().toString());
        agentMessage.setSender("agent");
        agentMessage.setTimestamp(System.currentTimeMillis());
        agentMessage.setAgentType(response.getType());

        if (response.isSuggestion()) {
            agentMessage.setContent(response.getContent());
            broadcastAgentMessage(agentMessage);
        } else if (response.isTask()) {
            agentClient.plan(response.getContent(), originalMessage.getContent(), agentSessionId)
                    .thenAccept(plan -> handlePlanResponse(plan, originalMessage, agentSessionId))
                    .exceptionally(ex -> {
                        logger.error("Plan generation failed", ex);
                        return null;
                    });
        }
    }

    private void handlePlanResponse(PlanResponse plan, ChatMessage originalMessage, String agentSessionId) {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            return;
        }

        ChatMessage planMessage = new ChatMessage();
        planMessage.setId("progress-" + plan.getTask());
        planMessage.setSender("agent");
        planMessage.setTimestamp(System.currentTimeMillis());
        planMessage.setAgentType("plan");

        StringBuilder sb = new StringBuilder("已生成执行计划:\n");
        for (int i = 0; i < plan.getSteps().size(); i++) {
            sb.append(i + 1).append(". ").append(plan.getSteps().get(i).getName()).append("\n");
        }
        planMessage.setContent(sb.toString().trim());
        planMessage.setSteps(plan.getSteps());

        try {
            String planJson = objectMapper.writeValueAsString(planMessage);
            sessionManager.broadcast(planJson);
            logger.info("Plan broadcast: task={}, steps={}", plan.getTask(), plan.getSteps().size());
        } catch (JsonProcessingException ex) {
            logger.error("Failed to serialize plan message", ex);
        }

        agentClient.execute(plan, agentSessionId)
                .exceptionally(ex -> {
                    logger.error("Execute failed", ex);
                    return null;
                });
    }

    private void broadcastAgentMessage(ChatMessage msg) {
        try {
            String json = objectMapper.writeValueAsString(msg);
            sessionManager.broadcast(json);
        } catch (JsonProcessingException ex) {
            logger.error("Failed to broadcast agent message", ex);
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
        String agentSessionId = agentSessionMap.remove(session.getId());
        logger.info("WebSocket disconnected: sessionId={}, agentSessionId={}, reason={}",
                session.getId(), agentSessionId, status);
    }
}
