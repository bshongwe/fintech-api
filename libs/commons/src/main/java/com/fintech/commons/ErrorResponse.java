package com.fintech.commons;

import java.time.LocalDateTime;
import java.util.Map;

public class ErrorResponse {
    private String code;
    private String message;
    private Map<String, Object> details;
    private LocalDateTime timestamp;
    private String path;
    
    public ErrorResponse() {}
    
    public ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String code;
        private String message;
        private Map<String, Object> details;
        private LocalDateTime timestamp;
        private String path;
        
        public Builder code(String code) {
            this.code = code;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }
        
        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder path(String path) {
            this.path = path;
            return this;
        }
        
        public ErrorResponse build() {
            ErrorResponse error = new ErrorResponse();
            error.code = this.code;
            error.message = this.message;
            error.details = this.details;
            error.timestamp = this.timestamp;
            error.path = this.path;
            return error;
        }
    }
    
    // Getters
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public Map<String, Object> getDetails() { return details; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getPath() { return path; }
}
