package com.worm.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentResponse {

    private String type;
    private String content;
    private Meta meta;

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

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
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

    public boolean isMention() {
        return "mention".equals(type);
    }

    public boolean requiresConfirmation() {
        return meta != null && Boolean.TRUE.equals(meta.getRequiresConfirmation());
    }

    public String suggestedTask() {
        return meta != null ? meta.getSuggestedTask() : null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta {
        private Boolean requiresConfirmation;
        @JsonProperty("suggested_task")
        private String suggestedTask;
        private Double confidence;
        @JsonProperty("info_sufficiency")
        private String infoSufficiency;
        @JsonProperty("missing_fields")
        private java.util.List<String> missingFields;

        public Boolean getRequiresConfirmation() {
            return requiresConfirmation;
        }

        public void setRequiresConfirmation(Boolean requiresConfirmation) {
            this.requiresConfirmation = requiresConfirmation;
        }

        public String getSuggestedTask() {
            return suggestedTask;
        }

        public void setSuggestedTask(String suggestedTask) {
            this.suggestedTask = suggestedTask;
        }

        public Double getConfidence() {
            return confidence;
        }

        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }

        public String getInfoSufficiency() {
            return infoSufficiency;
        }

        public void setInfoSufficiency(String infoSufficiency) {
            this.infoSufficiency = infoSufficiency;
        }

        public java.util.List<String> getMissingFields() {
            return missingFields;
        }

        public void setMissingFields(java.util.List<String> missingFields) {
            this.missingFields = missingFields;
        }
    }
}