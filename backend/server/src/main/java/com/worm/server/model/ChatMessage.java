package com.worm.server.model;

import java.util.List;
import java.util.Map;

import com.worm.server.dto.PlanStep;

public class ChatMessage {

    private String id;
    private String sender;
    private String content;
    private Long timestamp;
    private String agentType;
    private List<String> mentions;
    private List<PlanStep> steps;
    private String confirmTask;
    private List<Map<String, String>> slidesData;
    private String documentContent;
    private String downloadUrl;
    private String fileName;
    private Long fileSize;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getAgentType() {
        return agentType;
    }

    public void setAgentType(String agentType) {
        this.agentType = agentType;
    }

    public List<String> getMentions() {
        return mentions;
    }

    public void setMentions(List<String> mentions) {
        this.mentions = mentions;
    }

    public List<PlanStep> getSteps() {
        return steps;
    }

    public void setSteps(List<PlanStep> steps) {
        this.steps = steps;
    }

    public String getConfirmTask() {
        return confirmTask;
    }

    public void setConfirmTask(String confirmTask) {
        this.confirmTask = confirmTask;
    }

    public List<Map<String, String>> getSlidesData() {
        return slidesData;
    }

    public void setSlidesData(List<Map<String, String>> slidesData) {
        this.slidesData = slidesData;
    }

    public String getDocumentContent() {
        return documentContent;
    }

    public void setDocumentContent(String documentContent) {
        this.documentContent = documentContent;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public boolean isValid() {
        return sender != null && !sender.isBlank()
                && content != null && !content.isBlank();
    }

    public boolean isAgentMessage() {
        return agentType != null && !agentType.isBlank();
    }
}
