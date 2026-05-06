package com.worm.server.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class SessionContext {

    private String sessionId;
    private String activeTask;
    private String pendingTask;
    private List<String> history;
    private Map<String, String> collectedInfo;
    private List<String> missingFields;
    private boolean inInfoCollection;

    public SessionContext() {
        this.history = new CopyOnWriteArrayList<>();
        this.collectedInfo = new HashMap<>();
        this.missingFields = new ArrayList<>();
        this.inInfoCollection = false;
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

    public Map<String, String> getCollectedInfo() {
        return collectedInfo;
    }

    public void setCollectedInfo(String key, String value) {
        this.collectedInfo.put(key, value);
    }

    public void setCollectedInfoMap(Map<String, String> info) {
        this.collectedInfo = info != null ? info : new HashMap<>();
    }

    public String getCollectedInfo(String key) {
        return collectedInfo.get(key);
    }

    public List<String> getMissingFields() {
        return missingFields;
    }

    public void setMissingFields(List<String> missingFields) {
        this.missingFields = missingFields != null ? missingFields : new ArrayList<>();
    }

    public boolean isInInfoCollection() {
        return inInfoCollection;
    }

    public void setInInfoCollection(boolean inInfoCollection) {
        this.inInfoCollection = inInfoCollection;
    }

    public void resetInfoCollection() {
        this.collectedInfo.clear();
        this.missingFields.clear();
        this.inInfoCollection = false;
    }
}
