package com.user.activity.monitoring.configuration;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Класс конфигурации кафки для создания топиков.
 */
@Configuration
public class KafkaConfig {

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
