package com.worm.server.model;

import java.util.List;

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

    public boolean isValid() {
        return sender != null && !sender.isBlank()
                && content != null && !content.isBlank();
    }

    public boolean isAgentMessage() {
        return agentType != null && !agentType.isBlank();
    }
}
