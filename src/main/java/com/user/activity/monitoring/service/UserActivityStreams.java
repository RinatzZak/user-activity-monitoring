package com.user.activity.monitoring.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.user.activity.monitoring.enums.Action;
import com.user.activity.monitoring.model.UserCountActivity;
import com.user.activity.monitoring.model.UserActivity;
import com.user.activity.monitoring.serializer.JsonSerde;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.kstream.internals.SessionWindow;
import org.apache.kafka.streams.state.WindowStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableKafkaStreams
public class UserActivityStreams {
    @Value("${spring.kafka.topics.cdc-topic}")
    private String inputTopic;

    @Value("${spring.kafka.topics.output-topic-tumbling}")
    private String outputTopicForTumbling;

    @Value("${spring.kafka.topics.output-topic-hopping}")
    private String outputTopicForHopping;

    @Value("${spring.kafka.topics.output-topic-session}")
    private String outputTopicForSession;

    private final ActivityStorageService activityStorageService;
    private final JsonSerde<UserCountActivity> userSerde = new JsonSerde<>(UserCountActivity.class);

    @Bean
    public KStream<Long, UserActivity> buildTopology(StreamsBuilder streamsBuilder) {
        KStream<String, String> sourceStream = streamsBuilder.stream(
                inputTopic,
                Consumed.with(Serdes.String(), Serdes.String())
        );

        sourceStream.peek((key, value) -> {
            log.info("Получено сообщение из кафки: Ключ={}", key);
            log.debug("Полное значение: {}", value);
        });

        // Парсим Debezium событие
        KStream<Long, UserActivity> activityStream = sourceStream
                .filter((key, value) -> value != null && !value.isEmpty())
                .mapValues(this::parseDebeziumEvent)
                .filter((key, activity) -> activity != null)
                .selectKey((key, activity) -> activity.getUserId());

        activityStream.peek((userId, activity) -> {
            log.info("Найдена активность: userId={}, action={}, name={}",
                    userId, activity.getAction(), activity.getUserName());
        });

        activityStream.foreach((userId, activity) -> {
            activityStorageService.addActivity(activity);
            log.info("Активность сохранена в хранилище: userId={}, action={}", userId, activity.getAction());
        });

        // Агрегация метрик за 2 минуты. Для примера берем 2 минуты, задержка 30 секунд.
        KTable<Windowed<Long>, Long> tumblingWindowCounts = activityStream
                .groupByKey(Grouped.with(Serdes.Long(), new JsonSerde<>(UserActivity.class)))
                .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofMinutes(2), Duration.ofSeconds(30)))
                .count(Materialized.<Long, Long, WindowStore<Bytes, byte[]>>as("tumbling-window-counts")
                        .withKeySerde(Serdes.Long())
                        .withValueSerde(Serdes.Long()));

        tumblingWindowCounts.toStream()
                .peek((windowedKey, count) -> {
                    log.info("Агрегация метрик за 2 минуты [{} - {}]: User {} count = {}",
                            formatTime(windowedKey.window().start()),
                            formatTime(windowedKey.window().end()),
                            windowedKey.key(), count);
                })
                .map((windowedKey, count) -> {
                    // Преобразуем для отправки в топик
                    String outputKey = windowedKey.key().toString();
                    UserCountActivity outputValue = UserCountActivity
                            .builder()
                            .id(windowedKey.key())
                            .count(count)
                            .build();
                    return KeyValue.pair(outputKey, outputValue);
                })
                .to(outputTopicForTumbling, Produced.with(Serdes.String(), userSerde));


        // Скользящее среднее за 2 минуты
        KTable<Windowed<Long>, Long> hoppingWindowCounts = activityStream
                .groupByKey(Grouped.with(Serdes.Long(), new JsonSerde<>(UserActivity.class)))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1))
                        .advanceBy(Duration.ofSeconds(30)))
                .count(Materialized.as("hopping-window-counts"));

        hoppingWindowCounts.toStream()
                .peek((windowedKey, count) ->
                        log.info("Скользящее среднее за 2 минуты [{} - {}]: User {} count = {}",
                                formatTime(windowedKey.window().start()),
                                formatTime(windowedKey.window().end()),
                                windowedKey.key(), count)
                )
                .map((windowedKey, count) -> {
                    // Преобразуем для отправки в топик
                    String outputKey = windowedKey.key().toString();
                    UserCountActivity outputValue = UserCountActivity
                            .builder()
                            .id(windowedKey.key())
                            .count(count)
                            .build();
                    return KeyValue.pair(outputKey, outputValue);
                })
                .to(outputTopicForHopping, Produced.with(Serdes.String(), userSerde));

        // Анализ пользовательских сессий: сессия закрывается после 3 секунд неактивности
        KTable<Windowed<Long>, Long> sessionWindowCounts = activityStream
                .groupByKey(Grouped.with(Serdes.Long(), new JsonSerde<>(UserActivity.class)))
                .windowedBy(SessionWindows
                        .ofInactivityGapWithNoGrace(Duration.ofSeconds(3)))
                .count(Materialized.as("session-window-counts"));

        sessionWindowCounts.toStream()
                .peek((windowedKey, count) -> {
                    SessionWindow sessionWindow = (SessionWindow) windowedKey.window();
                    log.info("Сессия пользователя {}: {} действий, длительность {} сек ({} - {})",
                            windowedKey.key(),
                            count,
                            (sessionWindow.end() - sessionWindow.start()) / 1000,
                            formatTime(sessionWindow.start()),
                            formatTime(sessionWindow.end())
                    );
                })
                .filter((windowedKey, count) -> count != null)
                .map((windowedKey, count) -> {
                    return KeyValue.pair(
                            windowedKey.key().toString(),
                            UserCountActivity.builder()
                                    .id(windowedKey.key())
                                    .count(count)
                                    .build()
                    );
                })
                .to(outputTopicForSession, Produced.with(Serdes.String(), userSerde));




        return activityStream;
    }

    /**
     * Парсит Debezium событие из JSON.
     */
    private UserActivity parseDebeziumEvent(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            String operation = root.has("op") ? root.get("op").asText() : null;

            // Для insert (c) и update (u) берем "after"
            // Для delete (d) берем "before"
            JsonNode dataNode;
            if ("d".equals(operation)) {
                dataNode = root.get("before");
                if (dataNode == null || dataNode.isNull()) {
                    log.debug("DataNode для before null или empty");
                    return null;
                }
            } else {
                dataNode = root.get("after");
                if (dataNode == null || dataNode.isNull()) {
                    log.debug("DataNode для after null или empty, operation: {}", operation);
                    return null;
                }
            }

            Long userId = dataNode.has("id") ? dataNode.get("id").asLong() : null;
            String userName = dataNode.has("name") ? dataNode.get("name").asText() : null;
            String action = dataNode.has("action") ? dataNode.get("action").asText() : Action.UNKNOWN.name();
            String email = dataNode.has("email") ? dataNode.get("email").asText() : "";

            if (userId == null) {
                log.warn("Пользователь не найден. Событие: {}", dataNode);
                return null;
            }

            log.debug("Распарсили: userId={}, name={}, action={}, email={}, op={}",
                    userId, userName, action, email, operation);

            return UserActivity.builder()
                    .userId(userId)
                    .userName(userName)
                    .action(Action.getAction(action))
                    .email(email)
                    .eventTime(System.currentTimeMillis())
                    .build();

        } catch (Exception e) {
            log.error("Произошла ошибка при парсинге Debezium event: {}", json, e);
            return null;
        }
    }

    private String formatTime(Long timestamp) {
        if (timestamp == null) return "N/A";
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
        ).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
