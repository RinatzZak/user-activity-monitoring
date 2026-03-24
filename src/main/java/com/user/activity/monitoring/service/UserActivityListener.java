package com.user.activity.monitoring.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.user.activity.monitoring.model.Spammer;
import com.user.activity.monitoring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Слушатель для активности пользователя и его блокировки.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserActivityListener {
    private final UserRepository repository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "user-activity-stats-session", groupId = "user-activity")
    public void handleActivity(String message) {
        log.info("Блокируем нарушителя {}", message);
        Spammer spammer = null;
        try {
            spammer = objectMapper.readValue(message, Spammer.class);
            var user = repository.findById(spammer.getId());
            if (user.isPresent()) {
                var forSave = user.get();
                forSave.setIsBlocked(true);
                forSave.setBlockedAt(LocalDateTime.now());
                repository.save(forSave);
                log.info("Пользователь с id: {}, заблокирован на 2 минуты", forSave.getId());
            } else {
                log.info("Пользователь с id: {}, не найден", spammer.getId());
            }
        } catch (JsonProcessingException e) {
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
            Spammer spammer = objectMapper.readValue(message, Spammer.class);
            log.info("User {} сделал {} действий за последние 2 минуты (с шагом 30 сек)",
                    spammer.getId(), spammer.getCount());

            int hoppingThreshold = 8;
            if (spammer.getCount() > hoppingThreshold) {
                log.warn("Высокая активность! User {} сделал {} действий",
                        spammer.getId(), spammer.getCount());

                sendAlert(spammer.getId(), spammer.getCount());
            }

        } catch (Exception e) {
            log.error("Ошибка обработки Hopping Window сообщения: {}", message, e);
        }
    }

    /**
     * Обработка Session Window (сессионные окна)
     */
    @KafkaListener(topics = "user-activity-stats-session", groupId = "user-activity")
    public void handleSessionWindow(String message) {
        log.info("Топик: user-activity-stats-session. Получено сообщение: {}", message);

        try {
            Spammer spammer = objectMapper.readValue(message, Spammer.class);

            log.info("User {} сделал {} действий за сессию",
                    spammer.getId(), spammer.getCount());

            // Для сессий можно анализировать поведение
            if (spammer.getCount() > 15) {
                log.warn("Подозрительная сессия! User {} сделал {} действий",
                        spammer.getId(), spammer.getCount());
            }

        } catch (Exception e) {
            log.error("Ошибка обработки Session Window сообщения: {}", message, e);
        }
    }

    private void sendAlert(Long userId, Long count) {
        log.info("Отправлено предупреждение для пользователя {} (активность: {})", userId, count);
    }
}
