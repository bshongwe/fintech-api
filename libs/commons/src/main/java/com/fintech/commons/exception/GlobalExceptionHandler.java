package com.fintech.commons.exception;

import com.fintech.commons.ApiResponse;
import com.fintech.commons.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global Exception Handler
 * 
 * Provides centralized exception handling across all services.
 * Ensures consistent error responses and prevents stack trace exposure.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Handle validation errors from @Valid annotations
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("Input validation failed")
                .details(errors)
                .timestamp(LocalDateTime.now())
                .path(getPath(request))
                .build();
        
        logger.warn("Validation error: {} for request: {}", errors, getPath(request));
        
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(null, errorResponse));
    }
    
    /**
     * Handle bind errors
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Object>> handleBindErrors(
            BindException ex, WebRequest request) {
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("BIND_ERROR")
                .message("Data binding failed")
                .details(errors)
                .timestamp(LocalDateTime.now())
                .path(getPath(request))
                .build();
        
        logger.warn("Bind error: {} for request: {}", errors, getPath(request));
        
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(null, errorResponse));
    }
    
    /**
     * Handle constraint violations from @Validated
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolations(
            ConstraintViolationException ex, WebRequest request) {
        
        Map<String, String> errors = ex.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        ConstraintViolation::getMessage
                ));
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("CONSTRAINT_VIOLATION")
                .message("Constraint validation failed")
                .details(errors)
                .timestamp(LocalDateTime.now())
                .path(getPath(request))
                .build();
        
        logger.warn("Constraint violation: {} for request: {}", errors, getPath(request));
        
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(null, errorResponse));
    }
    
    /**
     * Handle method argument type mismatch
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, WebRequest request) {
        
        String error = String.format("Parameter '%s' should be of type '%s'", 
                ex.getName(), ex.getRequiredType().getSimpleName());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("TYPE_MISMATCH")
                .message("Parameter type mismatch")
                .details(Map.of("parameter", ex.getName(), "expectedType", ex.getRequiredType().getSimpleName()))
                .timestamp(LocalDateTime.now())
                .path(getPath(request))
                .build();
        
        logger.warn("Type mismatch error: {} for request: {}", error, getPath(request));
        
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(null, errorResponse));
    }
    
    /**
     * Handle authentication errors
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadCredentials(
            BadCredentialsException ex, WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("AUTHENTICATION_FAILED")
                .message("Invalid credentials provided")
                .timestamp(LocalDateTime.now())
                .path(getPath(request))
                .build();
        
        logger.warn("Authentication failed for request: {}", getPath(request));
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(null, errorResponse));
    }
    
    /**
     * Handle authorization errors
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("ACCESS_DENIED")
                .message("Insufficient permissions to access this resource")
                .timestamp(LocalDateTime.now())
                .path(getPath(request))
                .build();
        
        logger.warn("Access denied for request: {}", getPath(request));
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(null, errorResponse));
    }
    
    /**
     * Handle business logic exceptions
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(
            BusinessException ex, WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ex.getCode())
                .message(ex.getMessage())
                .details(ex.getDetails())
                .timestamp(LocalDateTime.now())
                .path(getPath(request))
                .build();
        
        logger.warn("Business exception: {} - {} for request: {}", 
                ex.getCode(), ex.getMessage(), getPath(request));
        
        return ResponseEntity.status(ex.getHttpStatus())
                .body(new ApiResponse<>(null, errorResponse));
    }
    
    /**
     * Handle resource not found exceptions
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFound(
            ResourceNotFoundException ex, WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("RESOURCE_NOT_FOUND")
                .message(ex.getMessage())
                .details(ex.getDetails())
                .timestamp(LocalDateTime.now())
                .path(getPath(request))
                .build();
        
        logger.warn("Resource not found: {} for request: {}", ex.getMessage(), getPath(request));
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(null, errorResponse));
    }
    
    /**
     * Handle illegal argument exceptions
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("INVALID_ARGUMENT")
                .message("Invalid argument provided")
                .timestamp(LocalDateTime.now())
                .path(getPath(request))
                .build();
        
        logger.warn("Illegal argument: {} for request: {}", ex.getMessage(), getPath(request));
        
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(null, errorResponse));
    }
    
    /**
     * Handle all other exceptions - NEVER expose stack traces
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(
            Exception ex, WebRequest request) {
        
        // Log the full exception for debugging (not exposed to client)
        logger.error("Unexpected error occurred for request: {}", getPath(request), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred. Please try again later.")
                .timestamp(LocalDateTime.now())
                .path(getPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(null, errorResponse));
    }
    
    /**
     * Extract path from web request
     */
    private String getPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
