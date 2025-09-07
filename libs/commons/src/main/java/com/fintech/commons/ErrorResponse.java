package com.fintech.commons;

public record ErrorResponse(String error, String errorDescription, String traceId) {}
