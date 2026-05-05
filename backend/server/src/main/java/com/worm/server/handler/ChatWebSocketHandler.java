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
import com.worm.server.model.SessionContext;
import com.worm.server.service.SessionManager;
import com.worm.server.service.TaskService;

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
    private final TaskService taskService;

    private final ConcurrentHashMap<String, String> agentSessionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SessionContext> contextMap = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(SessionManager sessionManager, ObjectMapper objectMapper,
            AgentClient agentClient, TaskService taskService) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
        this.agentClient = agentClient;
        this.taskService = taskService;
    }

    private static final List<String> CONFIRM_KEYWORDS = List.of(
            "开始", "执行", "生成", "确认", "好的", "可以", "去做吧", "go", "run", "yes", "ok");

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionManager.addSession(session);
        String agentSessionId = UUID.randomUUID().toString();
        agentSessionMap.put(session.getId(), agentSessionId);
        contextMap.computeIfAbsent(agentSessionId, id -> new SessionContext(agentSessionId));
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

            String agentSessionId = agentSessionMap.get(session.getId());
            SessionContext context = contextMap.computeIfAbsent(agentSessionId,
                    id -> new SessionContext(agentSessionId));

            logger.info("Incoming: sessionId={}, sender={}, content={}, mentions={}, confirmTask={}",
                    session.getId(), chatMessage.getSender(), chatMessage.getContent(),
                    chatMessage.getMentions(), chatMessage.getConfirmTask());

            String outboundJson = objectMapper.writeValueAsString(chatMessage);
            sessionManager.broadcast(outboundJson);

            context.appendHistory(chatMessage.getContent());

            List<String> mentions = chatMessage.getMentions();
            boolean shouldCallAgent = (mentions == null || mentions.isEmpty() || mentions.contains("agent"));

            if (!shouldCallAgent) {
                logger.info("Message directed to others (mentions={}), skipping agent", mentions);
                return;
            }

            if (chatMessage.getConfirmTask() != null && !chatMessage.getConfirmTask().isBlank()) {
                handleExplicitConfirm(chatMessage.getConfirmTask(), chatMessage, agentSessionId, context);
                return;
            }

            if (context.getPendingTask() != null && isConfirmMessage(chatMessage.getContent())) {
                handleImplicitConfirm(context.getPendingTask(), chatMessage, agentSessionId, context);
                return;
            }

            agentClient.process(chatMessage.getContent(), agentSessionId, mentions)
                    .thenAccept(response -> handleAgentResponse(response, chatMessage, agentSessionId, context))
                    .exceptionally(ex -> {
                        logger.error("Agent async processing failed", ex);
                        return null;
                    });

        } catch (JsonProcessingException ex) {
            logger.warn("Invalid chat message payload from session {}: {}", session.getId(), message.getPayload(), ex);
        }
    }

    private boolean isConfirmMessage(String content) {
        String lower = content.toLowerCase().trim();
        for (String kw : CONFIRM_KEYWORDS) {
            if (lower.equals(kw))
                return true;
            if (lower.startsWith(kw) && lower.length() <= kw.length() + 2)
                return true;
        }
        return false;
    }

    private void handleExplicitConfirm(String confirmTask, ChatMessage originalMessage,
            String agentSessionId, SessionContext context) {
        logger.info("Explicit confirm: task={}", confirmTask);
        context.setActiveTask(confirmTask);
        context.setPendingTask(null);
        triggerTaskExecution(confirmTask, originalMessage.getContent(), agentSessionId, context);
    }

    private void handleImplicitConfirm(String pendingTask, ChatMessage originalMessage,
            String agentSessionId, SessionContext context) {
        logger.info("Implicit confirm: pendingTask={}, message={}", pendingTask, originalMessage.getContent());
        context.setActiveTask(pendingTask);
        context.setPendingTask(null);
        triggerTaskExecution(pendingTask, originalMessage.getContent(), agentSessionId, context);
    }

    private void triggerTaskExecution(String taskType, String userMessage, String agentSessionId,
            SessionContext context) {
        broadcastTaskConfirm(taskType);

        agentClient.plan(taskType, userMessage, agentSessionId)
                .thenAccept(plan -> handlePlanResponse(plan, agentSessionId))
                .exceptionally(ex -> {
                    logger.error("Plan generation failed, running directly", ex);
                    taskService.execute(taskType, context);
                    broadcastComplete(taskType);
                    return null;
                });
    }

    private void handleAgentResponse(AgentResponse response, ChatMessage originalMessage,
            String agentSessionId, SessionContext context) {
        if (response == null || response.isIgnore() || response.isMention()) {
            return;
        }

        if (response.isSuggestion()) {
            if (response.requiresConfirmation() && response.suggestedTask() != null) {
                context.setPendingTask(response.suggestedTask());
                logger.info("Pending task set: sessionId={}, pendingTask={}",
                        agentSessionId, response.suggestedTask());
            }
            broadcastSuggestion(response);
        } else if (response.isTask()) {
            String taskType = response.getContent();
            if (taskType == null || taskType.isBlank()) {
                taskType = response.suggestedTask();
            }
            if (taskType == null || taskType.isBlank()) {
                return;
            }
            context.setActiveTask(taskType);
            context.setPendingTask(null);
            triggerTaskExecution(taskType, originalMessage.getContent(), agentSessionId, context);
        }
    }

    private void handlePlanResponse(PlanResponse plan, String agentSessionId) {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            return;
        }

        SessionContext context = contextMap.get(agentSessionId);

        ChatMessage planMessage = new ChatMessage();
        planMessage.setId("progress-" + plan.getTask());
        planMessage.setSender("agent");
        planMessage.setTimestamp(System.currentTimeMillis());
        planMessage.setAgentType("plan");

        StringBuilder sb = new StringBuilder("任务执行中: " + plan.getTask() + "\n");
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

        if (context != null) {
            taskService.execute(plan.getTask(), context);
        }
        broadcastComplete(plan.getTask());
    }

    private void broadcastSuggestion(AgentResponse response) {
        ChatMessage msg = new ChatMessage();
        msg.setId(UUID.randomUUID().toString());
        msg.setSender("agent");
        msg.setTimestamp(System.currentTimeMillis());
        msg.setAgentType("suggestion");
        msg.setContent(response.getContent());
        msg.setConfirmTask(response.suggestedTask());
        broadcastAgentMessage(msg);
    }

    private void broadcastTaskConfirm(String taskType) {
        ChatMessage msg = new ChatMessage();
        msg.setId(UUID.randomUUID().toString());
        msg.setSender("agent");
        msg.setTimestamp(System.currentTimeMillis());
        msg.setAgentType("task");
        msg.setContent("收到确认，开始执行: " + taskType);
        broadcastAgentMessage(msg);
    }

    private void broadcastComplete(String taskType) {
        ChatMessage msg = new ChatMessage();
        msg.setId("progress-" + taskType);
        msg.setSender("agent");
        msg.setTimestamp(System.currentTimeMillis());
        msg.setAgentType("progress");
        msg.setContent("任务已完成: " + taskType);
        broadcastAgentMessage(msg);
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
        contextMap.remove(agentSessionId);
        logger.info("WebSocket disconnected: sessionId={}, agentSessionId={}, reason={}",
                session.getId(), agentSessionId, status);
    }
}
