package com.worm.server.controller;

import java.util.List;
import java.util.Map;

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

        boolean isComplete = completed == total;

        ChatMessage progressMsg = new ChatMessage();
        progressMsg.setId("progress-" + progress.getTask());
        progressMsg.setSender("agent");
        progressMsg.setTimestamp(System.currentTimeMillis());
        progressMsg.setAgentType("progress");

        if (isComplete && progress.getResult() != null) {
            Map<String, Object> result = progress.getResult();
            StringBuilder content = new StringBuilder("任务已完成");

            Object deliverablesObj = result.get("deliverables");
            if (deliverablesObj instanceof List<?> deliverablesList && !deliverablesList.isEmpty()) {
                for (Object item : deliverablesList) {
                    if (item instanceof Map<?, ?> d) {
                        if (d.get("file_name") instanceof String fn) {
                            content.append("\n文件: ").append(fn);
                            if (progressMsg.getFileName() == null) {
                                progressMsg.setFileName(fn);
                            }
                        }
                        if (d.get("download_url") instanceof String du) {
                            String fullUrl = "/api/download" + du.substring(du.lastIndexOf('/'));
                            content.append("\n下载: ").append(fullUrl);
                            if (progressMsg.getDownloadUrl() == null) {
                                progressMsg.setDownloadUrl(fullUrl);
                            }
                        }
                        if (d.get("file_size") instanceof Integer fs) {
                            if (progressMsg.getFileSize() == null) {
                                progressMsg.setFileSize(fs.longValue());
                            }
                        }
                    }
                }
            } else {
                if (result.get("file_name") instanceof String fileName) {
                    content.append("\n文件: ").append(fileName);
                    progressMsg.setFileName(fileName);
                }
                if (result.get("download_url") instanceof String downloadUrl) {
                    String fullUrl = "/api/download" + downloadUrl.substring(downloadUrl.lastIndexOf('/'));
                    content.append("\n下载: ").append(fullUrl);
                    progressMsg.setDownloadUrl(fullUrl);
                }
                if (result.get("file_size") instanceof Integer fileSize) {
                    progressMsg.setFileSize(fileSize.longValue());
                }
            }
            progressMsg.setContent(content.toString());

            if (result.get("slides_data") instanceof List<?> slidesList && !slidesList.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<Map<String, String>> slidesData = (List<Map<String, String>>) slidesList;
                progressMsg.setSlidesData(slidesData);
            }

            if (result.get("generated_content") instanceof String genContent && !genContent.isBlank()) {
                progressMsg.setDocumentContent(genContent);
                if ("summarize_meeting".equals(progress.getTask())) {
                    progressMsg.setContent(genContent);
                }
            }
        } else {
            progressMsg.setContent(isComplete ? "任务已完成" : "任务执行中 (" + completed + "/" + total + ")");
        }
        progressMsg.setSteps(progress.getSteps());

        try {
            String json = objectMapper.writeValueAsString(progressMsg);
            sessionManager.broadcast(json);
            logger.info("Progress broadcast: task={}, completed={}/{}, running={}, hasResult={}",
                    progress.getTask(), completed, total, running,
                    isComplete && progress.getResult() != null);
        } catch (JsonProcessingException ex) {
            logger.error("Failed to serialize progress message", ex);
        }
    }
}
