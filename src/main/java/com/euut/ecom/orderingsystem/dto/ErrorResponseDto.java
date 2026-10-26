package com.euut.ecom.orderingsystem.dto;

import java.time.LocalDateTime;

public record ErrorResponseDto(
        int status,
        String message,
        LocalDateTime timestamp
) {
    public ErrorResponseDto(int status, String message) {
        this(status, message, LocalDateTime.now());
    }
}