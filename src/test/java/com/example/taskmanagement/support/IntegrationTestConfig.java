package com.example.taskmanagement.support;

import com.example.taskmanagement.service.DropboxService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;

@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestConfig {
    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(Instant.parse("2030-02-10T09:00:00Z"), ZoneOffset.UTC);
    }

    @Bean
    @Primary
    DropboxService inMemoryDropboxService() {
        return new InMemoryDropboxService();
    }

    @Bean
    @Primary
    JavaMailSender recordingMailSender() {
        return new RecordingMailSender();
    }
}
