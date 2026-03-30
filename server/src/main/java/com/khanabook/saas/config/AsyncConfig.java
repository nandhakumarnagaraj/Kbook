package com.khanabook.saas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "menuExtractionExecutor")
    public Executor menuExtractionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Limit to 4 simultaneous processes so CPU is not overloaded
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        // Can queue up to 1000 tasks
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("MenuParser-");
        executor.initialize();
        return executor;
    }
}
