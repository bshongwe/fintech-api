package com.fintech.commons.security;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.regex.Pattern;

/**
 * SQL Injection Prevention Utility
 * 
 * Provides utilities to prevent SQL injection attacks by:
 * - Input validation and sanitization
 * - Parameter binding helpers
 * - Query validation
 * - Dangerous pattern detection
 * 
 * CRITICAL: Always use parameterized queries (PreparedStatement) instead of string concatenation
 * 
 * @author Fintech Security Team
 */
@Component
public class SQLInjectionPrevention {
    
    private static final Logger log = LoggerFactory.getLogger(SQLInjectionPrevention.class);
    
    // Dangerous SQL patterns that should never appear in user input
    private static final Pattern[] DANGEROUS_PATTERNS = {
        Pattern.compile("('.+(\\-\\-|;|\\||\\*|%)).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*(union|select|insert|delete|update|drop|create|alter|exec|execute).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*(script|javascript|vbscript|onload|onerror).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*(<|>|&lt;|&gt;).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*(\\-\\-|;|\\||\\*).*"),
        Pattern.compile(".*('|\"|`|\\\\).*")
    };
    
    // SQL keywords that should be blocked in user input
    private static final String[] SQL_KEYWORDS = {
        "SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "CREATE", "ALTER", "EXEC", "EXECUTE",
        "UNION", "OR", "AND", "WHERE", "HAVING", "ORDER", "GROUP", "BY", "FROM", "INTO",
        "VALUES", "SET", "TRUNCATE", "REPLACE", "MERGE", "CALL", "EXPLAIN", "DESCRIBE",
        "--", "/*", "*/", ";", "||", "&&", "XOR"
    };
    
    /**
     * Validate input to prevent SQL injection
     * 
     * @param input User input to validate
     * @param fieldName Name of the field (for logging)
     * @return true if input is safe, false if potentially malicious
     */
    public boolean validateInput(String input, String fieldName) {
        if (input == null || input.trim().isEmpty()) {
            return true; // Empty input is safe
        }
        
        String trimmedInput = input.trim();
        
        // Check for dangerous patterns
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(trimmedInput).matches()) {
                log.warn("Potential SQL injection attempt detected in field '{}' - Pattern matched", fieldName);
                return false;
            }
        }
        
        // Check for SQL keywords (case-insensitive)
        String upperInput = trimmedInput.toUpperCase();
        for (String keyword : SQL_KEYWORDS) {
            if (upperInput.contains(keyword)) {
                log.warn("SQL keyword '{}' detected in field '{}' - Potential injection attempt", keyword, fieldName);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Sanitize input by removing potentially dangerous characters
     * 
     * WARNING: Sanitization is NOT sufficient protection. Always use parameterized queries.
     * This method should only be used as an additional layer of defense.
     * 
     * @param input Input to sanitize
     * @return Sanitized input
     */
    public String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }
        
        // Remove common SQL injection characters
        String sanitized = input
            .replaceAll("['\"`;\\-\\-]", "") // Remove quotes, semicolons, SQL comments
            .replaceAll("[<>]", "") // Remove HTML/XML characters
            .replaceAll("\\|\\|", "") // Remove SQL OR operator
            .replaceAll("\\bandor\\b", "") // Remove AND/OR words
            .trim();
        
        // Log if sanitization changed the input
        if (!input.equals(sanitized)) {
            log.info("Input sanitized - Original length: {}, Sanitized length: {}", 
                    input.length(), sanitized.length());
        }
        
        return sanitized;
    }
    
    /**
     * Validate SQL query to ensure it's safe for execution
     * 
     * @param query SQL query to validate
     * @return true if query appears safe, false otherwise
     */
    public boolean validateQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        
        String upperQuery = query.toUpperCase().trim();
        
        // Allow only SELECT, INSERT, UPDATE queries (block DDL)
        if (!upperQuery.startsWith("SELECT") && 
            !upperQuery.startsWith("INSERT") && 
            !upperQuery.startsWith("UPDATE")) {
            log.warn("Potentially dangerous query detected - Query starts with: {}", 
                    upperQuery.substring(0, Math.min(20, upperQuery.length())));
            return false;
        }
        
        // Check for dangerous operations
        String[] dangerousOperations = {"DROP", "DELETE", "TRUNCATE", "ALTER", "CREATE", "EXEC", "EXECUTE"};
        for (String operation : dangerousOperations) {
            if (upperQuery.contains(operation)) {
                log.warn("Dangerous operation '{}' detected in query", operation);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Create a safe PreparedStatement with parameter validation
     * 
     * @param connection Database connection
     * @param sql SQL query with parameter placeholders
     * @return PreparedStatement ready for parameter binding
     * @throws SQLException if statement creation fails
     * @throws SecurityException if query validation fails
     */
    public PreparedStatement createSafePreparedStatement(Connection connection, String sql) 
            throws SQLException, SecurityException {
        
        if (!validateQuery(sql)) {
            throw new SecurityException("Query validation failed - potentially unsafe query");
        }
        
        return connection.prepareStatement(sql);
    }
    
    /**
     * Set string parameter with additional validation
     * 
     * @param statement PreparedStatement
     * @param parameterIndex Parameter index (1-based)
     * @param value String value to set
     * @param fieldName Field name for logging
     * @throws SQLException if parameter setting fails
     * @throws SecurityException if value validation fails
     */
    public void setStringParameter(PreparedStatement statement, int parameterIndex, 
                                 String value, String fieldName) throws SQLException, SecurityException {
        
        if (!validateInput(value, fieldName)) {
            throw new SecurityException("Input validation failed for field: " + fieldName);
        }
        
        statement.setString(parameterIndex, value);
    }
    
    /**
     * Set string parameter with sanitization (use with caution)
     * 
     * @param statement PreparedStatement
     * @param parameterIndex Parameter index (1-based)
     * @param value String value to set
     * @param fieldName Field name for logging
     * @throws SQLException if parameter setting fails
     */
    public void setStringParameterWithSanitization(PreparedStatement statement, int parameterIndex, 
                                                  String value, String fieldName) throws SQLException {
        
        String sanitizedValue = sanitizeInput(value);
        statement.setString(parameterIndex, sanitizedValue);
        
        log.debug("Parameter set for field '{}' with sanitization", fieldName);
    }
    
    /**
     * Escape special characters for LIKE queries
     * 
     * @param searchTerm Search term for LIKE query
     * @return Escaped search term
     */
    public String escapeLikeParameter(String searchTerm) {
        if (searchTerm == null) {
            return null;
        }
        
        // Escape SQL wildcards and special characters
        return searchTerm
            .replace("\\", "\\\\") // Escape backslashes first
            .replace("%", "\\%")   // Escape percent
            .replace("_", "\\_")   // Escape underscore
            .replace("[", "\\[")   // Escape bracket
            .replace("'", "''");   // Escape single quotes
    }
    
    /**
     * Create a safe LIKE query with proper escaping
     * 
     * @param searchTerm Term to search for
     * @param position Position of wildcard (START, END, BOTH, NONE)
     * @return Safe search pattern for LIKE queries
     */
    public String createSafeLikePattern(String searchTerm, LikePosition position) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return "";
        }
        
        String escaped = escapeLikeParameter(searchTerm.trim());
        
        return switch (position) {
            case START -> "%" + escaped;
            case END -> escaped + "%";
            case BOTH -> "%" + escaped + "%";
            case NONE -> escaped;
        };
    }
    
    /**
     * Log potential SQL injection attempt for security monitoring
     * 
     * @param input Suspicious input
     * @param fieldName Field name
     * @param userContext User context (ID, IP, etc.)
     */
    public void logSecurityIncident(String input, String fieldName, String userContext) {
        log.error("SECURITY INCIDENT: Potential SQL injection attempt detected - " +
                "Field: '{}', User Context: '{}', Input Length: {}", 
                fieldName, userContext, input != null ? input.length() : 0);
        
        // In production, this should also:
        // 1. Send alert to security team
        // 2. Increment security metrics
        // 3. Consider blocking the user temporarily
        // 4. Log to security event system (SIEM)
    }
    
    /**
     * Position enum for LIKE query wildcards
     */
    public enum LikePosition {
        START,  // %searchterm
        END,    // searchterm%
        BOTH,   // %searchterm%
        NONE    // searchterm
    }
}
