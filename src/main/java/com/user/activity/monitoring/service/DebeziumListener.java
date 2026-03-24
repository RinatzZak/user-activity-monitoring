package com.user.activity.monitoring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация подключения для Debezium.
 */
@Slf4j
@Service
public class DebeziumListener {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${debezium.name:postgres-connector}")
    private String connectorName;

    @Value("${debezium.database.hostname:localhost}")
    private String dbHost;

    @Value("${debezium.database.port:5432}")
    private int dbPort;

    @Value("${debezium.database.user:user}")
    private String dbUser;

    @Value("${debezium.database.password:password}")
    private String dbPassword;

    @Value("${debezium.database.dbname:userdb}")
    private String dbName;

    @Value("${debezium.database.schema:public}")
    private String dbSchema;

    @Value("${debezium.topic-prefix:postgres-server}")
    private String topicPrefix;

    @Value("${debezium.host}")
    private String host;

    @Autowired
    public DebeziumListener(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostConstruct
    public void start() {
        log.info("Начинается обработка подключения Debezium");

        try {
            // Проверяем, существует ли уже коннектор
            if (isConnectorExists()) {
                log.info("Подключение Debezium уже существует.");
                checkConnectorStatus();
                return;
            }

            // Создаем конфигурацию коннектора
            Map<String, Object> connectorConfig = new HashMap<>();
            connectorConfig.put("connector.class", "io.debezium.connector.postgresql.PostgresConnector");
            connectorConfig.put("database.hostname", "postgres");
            connectorConfig.put("database.port", dbPort);
            connectorConfig.put("database.user", dbUser);
            connectorConfig.put("database.password", dbPassword);
            connectorConfig.put("database.dbname", dbName);
            connectorConfig.put("topic.prefix", topicPrefix);
            connectorConfig.put("table.include.list", "public.users");
            connectorConfig.put("plugin.name", "pgoutput");
            connectorConfig.put("slot.name", "debezium_slot");
            connectorConfig.put("publication.name", "debezium_pub");
            connectorConfig.put("key.converter", "org.apache.kafka.connect.json.JsonConverter");
            connectorConfig.put("value.converter", "org.apache.kafka.connect.json.JsonConverter");
            connectorConfig.put("key.converter.schemas.enable", "false");
            connectorConfig.put("value.converter.schemas.enable", "false");

            Map<String, Object> request = new HashMap<>();
            request.put("name", connectorName);
            request.put("config", connectorConfig);

            String jsonRequest = objectMapper.writeValueAsString(request);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(jsonRequest, headers);

            // Отправляем запрос на создание коннектора
            String url = host;
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.CREATED) {
                log.info("Подключение прошло успешно: {}", response.getBody());
            } else {
                log.error("Не удалось создать подключение: {}", response.getBody());
            }
            Thread.sleep(500);
            checkConnectorStatus();

        } catch (Exception e) {
            log.error("Ошибка подключения Debezium", e);
        }
    }

    private boolean isConnectorExists() {
        try {
            String url = host + connectorName;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            return false;
        }
    }

    private void checkConnectorStatus() {
        try {
            String url = host + connectorName + "/status";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            log.info("Статус подключения: {}", response.getBody());
        } catch (Exception e) {
            log.error("Не удалось проверить статус подключения", e);
        }
    }
}
