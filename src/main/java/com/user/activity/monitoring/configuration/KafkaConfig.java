package com.user.activity.monitoring.configuration;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.SslConfigs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

/**
 * Класс конфигурации кафки для создания топиков.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.ssl.trust-store-location}")
    private String truststoreLocation;

    @Value("${spring.kafka.properties.ssl.trust-store-password}")
    private String truststorePassword;

    @Value("${spring.kafka.properties.ssl.key-store-location}")
    private String keystoreLocation;

    @Value("${spring.kafka.properties.ssl.key-store-password}")
    private String keystorePassword;

    @Value("${spring.kafka.properties.ssl.key-password}")
    private String keyPassword;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();

        // Основные настройки
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(AdminClientConfig.CLIENT_ID_CONFIG, "kafka-admin");

        // SSL настройки
        configs.put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, "SSL");
        configs.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, truststoreLocation);
        configs.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword);
        configs.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, keystoreLocation);
        configs.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, keystorePassword);
        configs.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, keyPassword);
        configs.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "");
        configs.put(SslConfigs.SSL_PROTOCOL_CONFIG, "TLSv1.2");
        configs.put(SslConfigs.SSL_ENABLED_PROTOCOLS_CONFIG, "TLSv1.2");

        return new KafkaAdmin(configs);
    }

    @Bean
    public NewTopic cdcTopic(@Value("${spring.kafka.topics.cdc-topic}") String topicName) {
        return new NewTopic(
                topicName,
                3,
                (short) 1
        );
    }

    @Bean
    public NewTopic outputTopicForTumbling(@Value("${spring.kafka.topics.output-topic-tumbling}") String topicName) {
        return new NewTopic(
                topicName,
                3,
                (short) 1
        );
    }

    @Bean
    public NewTopic outputTopicForHopping(@Value("${spring.kafka.topics.output-topic-hopping}") String topicName) {
        return new NewTopic(
                topicName,
                3,
                (short) 1
        );
    }

    @Bean
    public NewTopic outputTopicForSession(@Value("${spring.kafka.topics.output-topic-session}") String topicName) {
        return new NewTopic(
                topicName,
                3,
                (short) 1
        );
    }
}
