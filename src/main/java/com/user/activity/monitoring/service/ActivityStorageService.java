package com.user.activity.monitoring.service;

import com.user.activity.monitoring.enums.StorageStrategy;
import com.user.activity.monitoring.model.UserActivity;
import com.user.activity.monitoring.service.strategy.ActivityStore;
import com.user.activity.monitoring.service.strategy.InMemoryStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Сервис для работы с активностью пользователей.
 */
@Slf4j
@Service
public class ActivityStorageService {
    private final Map<StorageStrategy, ActivityStore> stores = new EnumMap<>(StorageStrategy.class);

    @Value("${app.storage.strategy:IN_MEMORY}")
    private String storageStrategyName;

    @PostConstruct
    public void init() {
        stores.put(StorageStrategy.IN_MEMORY, new InMemoryStore());

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(this::cleanupAll, 1, 1, TimeUnit.HOURS);
    }

    private ActivityStore getStore() {
        log.info("Тип хранение активности: {}", storageStrategyName);
        StorageStrategy strategy = StorageStrategy.valueOf(storageStrategyName);
        return stores.get(strategy);
    }

    public void addActivity(UserActivity activity) {
        log.info("Добавляем активность: {}", activity);
        getStore().add(activity);
    }

    public List<UserActivity> getRecentActivities(String action, int limit) {
        log.info("Получение всех активностей = {}", action);
        return getStore().getRecent(action, limit);
    }

    public List<UserActivity> getUserActivities(Long userId, int limit) {
        log.info("Получение активности у пользователя с id = {}", userId);
        return getStore().getByUser(userId, limit);
    }

    public void cleanupAll() {
        stores.values().forEach(ActivityStore::cleanup);
    }
}
