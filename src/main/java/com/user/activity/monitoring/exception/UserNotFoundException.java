package com.user.activity.monitoring.exception;

import lombok.Getter;

/**
 * Кастомное исключение, что пользователь не найден.
 */
@Getter
public class UserNotFoundException extends RuntimeException {
    private final Long userId;
    public UserNotFoundException(Long userId) {
        super(String.format("Пользователь с id %d не найден", userId));
        this.userId = userId;
    }
}
