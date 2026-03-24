package com.user.activity.monitoring.service.strategy;

import com.user.activity.monitoring.model.UserActivity;

import java.util.List;

/**
 * Интерфейс для стратегий хранения.
 */
public interface ActivityStore {

    void add(UserActivity activity);

    List<UserActivity> getRecent(String action, int limit);

    List<UserActivity> getByUser(Long userId, int limit);

    void cleanup();
}
