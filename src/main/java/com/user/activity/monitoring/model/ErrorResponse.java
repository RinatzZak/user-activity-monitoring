package com.user.activity.monitoring.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Дто для ответа с ошибкой.
 */
@Data
@Builder
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private String path;
    private LocalDateTime timestamp;
    private Long userId;
    private String unblockTime;
}
