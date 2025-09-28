package com.fintech.commons.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Business Exception
 * 
 * Used for business logic violations and domain-specific errors.
 */
public class BusinessException extends RuntimeException {
    
    private final String code;
    private final HttpStatus httpStatus;
    private final Map<String, Object> details;
    
    public BusinessException(String code, String message) {
        this(code, message, HttpStatus.BAD_REQUEST, null);
    }
    
    public BusinessException(String code, String message, HttpStatus httpStatus) {
        this(code, message, httpStatus, null);
    }
    
    public BusinessException(String code, String message, Map<String, Object> details) {
        this(code, message, HttpStatus.BAD_REQUEST, details);
    }
    
    public BusinessException(String code, String message, HttpStatus httpStatus, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = details;
    }
    
    public String getCode() {
        return code;
    }
    
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
    
    public Map<String, Object> getDetails() {
        return details;
    }
}
