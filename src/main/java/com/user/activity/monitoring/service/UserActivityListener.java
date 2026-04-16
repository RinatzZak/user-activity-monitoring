package com.user.activity.monitoring.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.user.activity.monitoring.model.UserCountActivity;
import com.user.activity.monitoring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Слушатель для активности пользователя и его блокировки.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserActivityListener {
    @Value("${app.block.threshold.session}")
    private int sessionThreshold;
    @Value("${app.block.threshold.hopping}")
    private int hoppingThreshold;
    @Value("${app.block.threshold.tumbling}")
    private int tumblingThreshold;
    private final UserRepository repository;
    private final ObjectMapper objectMapper;


    @RetryableTopic(
            kafkaTemplate = "kafkaTemplate",
            attempts = "3",
            backoff = @Backoff(
                    delay = 2000,
                    multiplier = 2
            ),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            dltTopicSuffix = ".ERROR"
    )
    @KafkaListener(topics = "user-activity-stats-session", groupId = "user-activity")
    public void handleActivity(String message) {
        log.info("Обработка сообщения: {}", message);
        UserCountActivity userCountActivity = null;
        try {
            userCountActivity = objectMapper.readValue(message, UserCountActivity.class);

            if (userCountActivity.getCount() >= sessionThreshold) {
                var user = repository.findById(userCountActivity.getId());
                if (user.isPresent()) {
                    var forSave = user.get();
                    forSave.setIsBlocked(true);
                    forSave.setBlockedAt(LocalDateTime.now());
                    repository.save(forSave);
                    log.info("Пользователь с id: {}, ЗАБЛОКИРОВАН! Действий за сессию: {}",
                            forSave.getId(), userCountActivity.getCount());
                } else {
                    log.warn("Пользователь с id: {}, не найден", userCountActivity.getId());
                }
            } else {
                log.info("Пользователь с id: {}, не заблокирован. Действий за сессию: {} (порог: {})",
                        userCountActivity.getId(), userCountActivity.getCount(), sessionThreshold);
            }

        } catch (JsonProcessingException e) {
            log.error("Ошибка десериализации сообщения: {}", message, e);
            throw new IllegalArgumentException("Ошибка десериализации сообщения", e);
        } catch (Exception e) {
            log.error("Ошибка обработки сообщения: {}", message, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Обработка Hopping Window (скользящие окна)
     */
    @KafkaListener(topics = "user-activity-stats-hopping", groupId = "user-activity")
    public void handleHoppingWindow(String message) {
        log.info("Топик:user-activity-stats-hopping. Получено сообщение: {}", message);

        try {
            UserCountActivity userCountActivity = objectMapper.readValue(message, UserCountActivity.class);
            log.info("User {} сделал {} действий за последние 2 минуты (с шагом 30 сек)",
                    userCountActivity.getId(), userCountActivity.getCount());


            if (userCountActivity.getCount() > hoppingThreshold) {
                log.warn("Высокая активность! User {} сделал {} действий",
                        userCountActivity.getId(), userCountActivity.getCount());

                sendAlert(userCountActivity.getId(), userCountActivity.getCount());
            }

        } catch (Exception e) {
            log.error("Ошибка обработки Hopping Window сообщения: {}", message, e);
        }
    }

    /**
     * Обработка Tumbling Window
     */
    @KafkaListener(topics = "${spring.kafka.topics.output-topic-tumbling}", groupId = "user-activity")
    public void handleSessionWindow(String message) {
        log.info("Топик: user-activity-stats-tumbling. Получено сообщение: {}", message);

        try {
            UserCountActivity userCountActivity = objectMapper.readValue(message, UserCountActivity.class);

            log.info("User {} сделал {} действий за сессию",
                    userCountActivity.getId(), userCountActivity.getCount());

            if (userCountActivity.getCount() > tumblingThreshold) {
                log.warn("Подозрительная сессия! User {} сделал {} действий",
                        userCountActivity.getId(), userCountActivity.getCount());
            }

        } catch (Exception e) {
            log.error("Ошибка обработки Session Window сообщения: {}", message, e);
        }
    }

    private void sendAlert(Long userId, Long count) {
        log.info("Отправлено предупреждение для пользователя {} (активность: {})", userId, count);
    }
}
