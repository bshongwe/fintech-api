package com.fintech.commons;

public record ApiResponse<T>(T data, ErrorResponse error) { }
