package com.user.activity.monitoring.configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;

import java.io.File;

@Configuration
@EnableKafka
public class SslConfig {
    @Value("${spring.kafka.properties.ssl.trust-store-location}")
    private String truststoreLocation;

    @Value("${spring.kafka.properties.ssl.key-store-location}")
    private String keystoreLocation;

    @PostConstruct
    public void init() {
        checkFileExists("Truststore", truststoreLocation);
        checkFileExists("Keystore", keystoreLocation);
    }

    private void checkFileExists(String name, String path) {
        File file = new File(path);
        if (!file.exists()) {
            throw new IllegalStateException(name + " file not found: " + path);
        }
        System.out.println(name + " loaded: " + file.getAbsolutePath());
    }
}
