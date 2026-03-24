package com.user.activity.monitoring.model;

import lombok.Builder;
import lombok.Data;

/**
 * Дто нарушителя.
 */
@Data
@Builder
public class Spammer {
    private Long id;
    private Long count;
}
