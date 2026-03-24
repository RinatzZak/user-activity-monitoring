package com.user.activity.monitoring.exception;

import lombok.Getter;

/**
 * Исключение для заблокированного пользователя.
 */
@Getter
public class UserBlockedException extends RuntimeException {

    private final Long userId;
    private final String unblockTime;

    public UserBlockedException(Long userId, String unblockTime) {
        super(String.format("Пользователь с id %d заблокирован до %s", userId, unblockTime));
        this.userId = userId;
        this.unblockTime = unblockTime;
    }

    public UserBlockedException(Long userId) {
        super(String.format("Пользователь с id %d заблокирован", userId));
        this.userId = userId;
        this.unblockTime = null;
    }
}
