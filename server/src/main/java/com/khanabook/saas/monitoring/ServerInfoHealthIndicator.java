package com.khanabook.saas.monitoring;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class ServerInfoHealthIndicator implements HealthIndicator {

    private final LocalDateTime startupTime = LocalDateTime.now();

    @Override
    public Health health() {
        return Health.up()
                .withDetail("lastUpdated", startupTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .withDetail("serverDate", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
    }
}
