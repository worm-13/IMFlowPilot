package com.worm.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentResponse {

    private String type;
    private String content;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isIgnore() {
        return "ignore".equals(type);
    }

    public boolean isSuggestion() {
        return "suggestion".equals(type);
    }

    public boolean isTask() {
        return "task".equals(type);
    }
}