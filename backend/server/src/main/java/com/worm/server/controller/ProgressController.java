package com.worm.server.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worm.server.dto.PlanResponse;
import com.worm.server.model.ChatMessage;
import com.worm.server.service.SessionManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProgressController {

    private static final Logger logger = LoggerFactory.getLogger(ProgressController.class);

    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public ProgressController(SessionManager sessionManager, ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/agent/progress")
    public void receiveProgress(@RequestBody PlanResponse progress) {
        if (progress == null || progress.getSteps() == null) {
            return;
        }

        long completed = progress.getSteps().stream()
                .filter(s -> "completed".equals(s.getStatus()))
                .count();
        long running = progress.getSteps().stream()
                .filter(s -> "running".equals(s.getStatus()))
                .count();
        long total = progress.getSteps().size();

        ChatMessage progressMsg = new ChatMessage();
        progressMsg.setId("progress-" + progress.getTask());
        progressMsg.setSender("agent");
        progressMsg.setTimestamp(System.currentTimeMillis());
        progressMsg.setAgentType("progress");
        progressMsg.setContent(completed == total ? "任务已完成" : "任务执行中 (" + completed + "/" + total + ")");
        progressMsg.setSteps(progress.getSteps());

        try {
            String json = objectMapper.writeValueAsString(progressMsg);
            sessionManager.broadcast(json);
            logger.info("Progress broadcast: task={}, completed={}/{}, running={}",
                    progress.getTask(), completed, total, running);
        } catch (JsonProcessingException ex) {
            logger.error("Failed to serialize progress message", ex);
        }
    }
}
