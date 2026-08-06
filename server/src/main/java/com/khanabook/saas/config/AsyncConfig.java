package com.khanabook.saas.config;

import com.khanabook.saas.security.TenantContext;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "menuExtractionExecutor")
    Executor menuExtractionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("MenuParser-");

        // Propagate TenantContext and MDC log context into async threads
        // so tenant isolation and tracing are preserved in @Async methods.
        executor.setTaskDecorator(runnable -> {
            Long tenantId = TenantContext.getCurrentTenant();
            String role   = TenantContext.getCurrentRole();
            Map<String, String> mdcContext = MDC.getCopyOfContextMap();
            return () -> {
                try {
                    if (tenantId != null) TenantContext.setCurrentTenant(tenantId);
                    if (role != null)     TenantContext.setCurrentRole(role);
                    if (mdcContext != null) MDC.setContextMap(mdcContext);
                    runnable.run();
                } finally {
                    TenantContext.clear();
                    MDC.clear();
                }
            };
        });

        executor.initialize();
        return executor;
    }

    @Bean(name = "postSplitExecutor")
    Executor postSplitExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("PostSplit-");

        executor.setTaskDecorator(runnable -> {
            Long tenantId = TenantContext.getCurrentTenant();
            String role   = TenantContext.getCurrentRole();
            Map<String, String> mdcContext = MDC.getCopyOfContextMap();
            return () -> {
                try {
                    if (tenantId != null) TenantContext.setCurrentTenant(tenantId);
                    if (role != null)     TenantContext.setCurrentRole(role);
                    if (mdcContext != null) MDC.setContextMap(mdcContext);
                    runnable.run();
                } finally {
                    TenantContext.clear();
                    MDC.clear();
                }
            };
        });

        executor.initialize();
        return executor;
    }
}
