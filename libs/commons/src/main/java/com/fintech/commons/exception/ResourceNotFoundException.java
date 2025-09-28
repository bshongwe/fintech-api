package com.fintech.commons.exception;

import java.util.Map;

/**
 * Resource Not Found Exception
 * 
 * Used when a requested resource cannot be found.
 */
public class ResourceNotFoundException extends RuntimeException {
    
    private final Map<String, Object> details;
    
    public ResourceNotFoundException(String message) {
        this(message, null);
    }
    
    public ResourceNotFoundException(String message, Map<String, Object> details) {
        super(message);
        this.details = details;
    }
    
    public Map<String, Object> getDetails() {
        return details;
    }
}
