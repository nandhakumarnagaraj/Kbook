package com.khanabook.saas.config;

import com.khanabook.saas.security.TenantContext;
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
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("MenuParser-");

        // Propagate TenantContext into async threads so tenant isolation
        // is preserved when @Async methods read TenantContext.getCurrentTenant().
        executor.setTaskDecorator(runnable -> {
            Long tenantId = TenantContext.getCurrentTenant();
            String role   = TenantContext.getCurrentRole();
            return () -> {
                try {
                    if (tenantId != null) TenantContext.setCurrentTenant(tenantId);
                    if (role != null)     TenantContext.setCurrentRole(role);
                    runnable.run();
                } finally {
                    TenantContext.clear();
                }
            };
        });

        executor.initialize();
        return executor;
    }
}
