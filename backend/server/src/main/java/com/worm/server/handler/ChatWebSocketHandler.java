package com.worm.server.handler;

import java.util.UUID;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            "开始", "执行", "确认", "确认生成", "开始生成", "开始执行");

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

            if (context.isInInfoCollection()) {
                int index = context.getCollectedInfo().size() + 1;
                context.setCollectedInfo("user_input_" + index, chatMessage.getContent());
                logger.info("Info collected: sessionId={}, key=user_input_{}, content={}",
                        agentSessionId, index, chatMessage.getContent());
            }

            String chatHistory = buildChatHistory(context);
            agentClient.process(chatMessage.getContent(), agentSessionId, mentions,
                    context.getPendingTask(), context.getCollectedInfo(), context.isInInfoCollection(),
                    context.getLastDocContent(), context.getLastPptContent(), chatHistory)
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

        String enrichedMessage = enrichMessageWithCollectedInfo(
                originalMessage.getContent(), context.getCollectedInfo());
        context.resetInfoCollection();

        triggerTaskExecution(confirmTask, enrichedMessage, agentSessionId, context);
    }

    private String enrichMessageWithCollectedInfo(String original, Map<String, String> collected) {
        if (collected == null || collected.isEmpty()) {
            return original;
        }
        StringBuilder sb = new StringBuilder(original);
        sb.append("\n\n[已收集信息]\n");
        for (Map.Entry<String, String> entry : collected.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
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
            AgentResponse.Meta meta = response.getMeta();
            String infoSufficiency = meta != null ? meta.getInfoSufficiency() : "unknown";

            if ("insufficient".equals(infoSufficiency) || "partial".equals(infoSufficiency)) {
                context.setInInfoCollection(true);
                context.setPendingTask(response.suggestedTask());
                if (meta != null && meta.getMissingFields() != null) {
                    context.setMissingFields(meta.getMissingFields());
                }
                logger.info("Info collection started: sessionId={}, pendingTask={}, missingFields={}",
                        agentSessionId, response.suggestedTask(), context.getMissingFields());
                broadcastInfoRequest(response);
            } else {
                if (response.requiresConfirmation() && response.suggestedTask() != null) {
                    context.setPendingTask(response.suggestedTask());
                    logger.info("Pending task set: sessionId={}, pendingTask={}",
                            agentSessionId, response.suggestedTask());
                }
                broadcastSuggestion(response);
            }
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
            context.resetInfoCollection();
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

        agentClient.execute(plan, agentSessionId,
                context != null ? context.getLastDocContent() : null,
                context != null ? context.getLastPptContent() : null)
                .thenAccept(result -> {
                    if (context != null && result != null && result.getResult() != null) {
                        Map<String, Object> execResult = result.getResult();
                        Object generatedContent = execResult.get("generated_content");
                        if (generatedContent instanceof String content && !content.isBlank()) {
                            if ("generate_doc".equals(plan.getTask()) || "modify_doc".equals(plan.getTask())) {
                                context.setLastDocContent(content);
                                logger.info("Stored lastDocContent for session {} ({} chars)",
                                        agentSessionId, content.length());
                            } else if ("generate_ppt".equals(plan.getTask()) || "modify_ppt".equals(plan.getTask())) {
                                context.setLastPptContent(content);
                                logger.info("Stored lastPptContent for session {} ({} chars)",
                                        agentSessionId, content.length());
                            }
                        }

                        Object slidesDataRaw = execResult.get("slides_data");
                        if (slidesDataRaw instanceof List<?> slidesList && !slidesList.isEmpty()) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, String>> slidesData = (List<Map<String, String>>) slidesList;
                            ChatMessage slidesMsg = new ChatMessage();
                            slidesMsg.setId("slides-" + plan.getTask());
                            slidesMsg.setSender("agent");
                            slidesMsg.setTimestamp(System.currentTimeMillis());
                            slidesMsg.setAgentType("slides_data");
                            slidesMsg.setContent("PPT 已生成，共 " + slidesData.size() + " 页");
                            slidesMsg.setSlidesData(slidesData);
                            broadcastAgentMessage(slidesMsg);
                            logger.info("Slides data broadcast: sessionId={}, slides={}",
                                    agentSessionId, slidesData.size());
                        }
                    }
                    if (context != null) {
                        taskService.execute(plan.getTask(), context);
                    }
                })
                .exceptionally(ex -> {
                    logger.error("Execute failed", ex);
                    return null;
                });
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

    private void broadcastInfoRequest(AgentResponse response) {
        ChatMessage msg = new ChatMessage();
        msg.setId(UUID.randomUUID().toString());
        msg.setSender("agent");
        msg.setTimestamp(System.currentTimeMillis());
        msg.setAgentType("info_request");
        msg.setContent(response.getContent());
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

    private String buildChatHistory(SessionContext context) {
        List<String> history = context.getHistory();
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            sb.append("[").append(i + 1).append("] ").append(history.get(i)).append("\n");
        }
        return sb.toString().trim();
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
