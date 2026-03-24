package com.user.activity.monitoring.service.strategy;

import com.user.activity.monitoring.model.UserActivity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Стратегия IN_MEMORY.
 */
@Slf4j
@Component
public class InMemoryStore implements ActivityStore {
    @Value("${app.storage.max-activities:1000}")
    private int maxActivities;

    @Value("${app.storage.ttl-hours:1}")
    private int ttlHours;
    private final List<UserActivity> activities = new CopyOnWriteArrayList<>();

    @Override
    public void add(UserActivity activity) {
        activities.add(activity);
    }

    @Override
    public List<UserActivity> getRecent(String action, int limit) {
        return activities.stream()
                .filter(a -> action == null || action.equals(a.getAction()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserActivity> getByUser(Long userId, int limit) {
        return activities.stream()
                .filter(a -> userId.equals(a.getUserId()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public void cleanup() {
        log.info("Очистка всех активностей");
        long cutoff = System.currentTimeMillis() - ((long) ttlHours * 60 * 60 * 1000);
        activities.removeIf(a -> a.getEventTime() < cutoff);
        while (activities.size() > maxActivities) {
            activities.remove(activities.size() - 1);
        }
    }
}
