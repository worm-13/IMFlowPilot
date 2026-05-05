package com.worm.server.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SessionContext {

    private String sessionId;
    private String activeTask;
    private String pendingTask;
    private List<String> history;

    public SessionContext() {
        this.history = new CopyOnWriteArrayList<>();
    }

    public SessionContext(String sessionId) {
        this();
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getActiveTask() {
        return activeTask;
    }

    public void setActiveTask(String activeTask) {
        this.activeTask = activeTask;
    }

    public String getPendingTask() {
        return pendingTask;
    }

    public void setPendingTask(String pendingTask) {
        this.pendingTask = pendingTask;
    }

    public List<String> getHistory() {
        return history;
    }

    public void appendHistory(String message) {
        history.add(message);
        if (history.size() > 10) {
            history.remove(0);
        }
    }
}
