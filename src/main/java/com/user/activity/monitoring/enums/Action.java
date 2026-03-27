package com.user.activity.monitoring.enums;

/**
 * Активности пользователя.
 */
public enum Action {
    REGISTER("Регистрация"),
    UPDATE("Обновление"),
    SCROLL("Пролистывание"),
    DELETE("Удаление"),
    UNKNOWN("Неизвестное действие");

    Action(String description) {
    }

    public static Action getAction(String description) {
        try {
            return Action.valueOf(description);
        } catch (IllegalArgumentException e) {
            return Action.UNKNOWN;
        }
    }
}
