package com.fintech.auth.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

@ControllerAdvice
public class OAuth2ErrorHandler {
    @ExceptionHandler(OAuth2Error.class)
    @ResponseBody
    public ResponseEntity<?> handleOAuth2Error(OAuth2Error ex) {
        // FAPI-compliant error response
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(ex.getErrorCode(), ex.getDescription()));
    }

    static class ErrorResponse {
        public String error;
        public String error_description;
        public ErrorResponse(String error, String error_description) {
            this.error = error;
            this.error_description = error_description;
        }
    }
}
