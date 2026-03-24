package com.user.activity.monitoring.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Дто для активности пользователя.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivity {
    private Long userId;
    private String userName;
    private String action;
    private Long eventTime;
    private Long windowStart;
    private Long windowEnd;
    private String email;
}
