package com.khanabook.saas.core.config;

import com.khanabook.saas.core.security.TenantContext;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * Propagates full TenantContext (tenantId, role, userId, terminal details)
     * and SLF4J MDC logging context into asynchronous worker threads.
     * Guarantees that ThreadLocals are strictly cleared in the finally block
     * to eliminate thread-pool tenant leakage risks.
     */
    private TaskDecorator tenantAndMdcTaskDecorator() {
        return runnable -> {
            Long tenantId = TenantContext.getCurrentTenant();
            String role   = TenantContext.getCurrentRole();
            Long userId   = TenantContext.getCurrentUserId();
            String termId = TenantContext.getCurrentTerminalId();
            String termSeries = TenantContext.getCurrentTerminalSeries();
            String termDevice = TenantContext.getCurrentTerminalDevice();
            Boolean termActive = TenantContext.getCurrentTerminalActive();
            Map<String, String> mdcContext = MDC.getCopyOfContextMap();

            return () -> {
                try {
                    if (tenantId != null)   TenantContext.setCurrentTenant(tenantId);
                    if (role != null)       TenantContext.setCurrentRole(role);
                    if (userId != null)     TenantContext.setCurrentUserId(userId);
                    if (termId != null)     TenantContext.setCurrentTerminalId(termId);
                    if (termSeries != null) TenantContext.setCurrentTerminalSeries(termSeries);
                    if (termDevice != null) TenantContext.setCurrentTerminalDevice(termDevice);
                    if (termActive != null) TenantContext.setCurrentTerminalActive(termActive);
                    if (mdcContext != null) MDC.setContextMap(mdcContext);
                    runnable.run();
                } finally {
                    TenantContext.clear();
                    MDC.clear();
                }
            };
        };
    }

    /**
     * Default TaskExecutor for unannotated @Async methods (e.g. EmailNotificationService).
     * Bounded, pooled, and decorated with tenant context.
     */
    @Primary
    @Bean(name = "taskExecutor")
    Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("KhanaAsync-");
        executor.setTaskDecorator(tenantAndMdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * Fire-and-forget FCM pushes (pushSyncNow, pushToRestaurant offload, etc.) run here
     * instead of the caller's HTTP request thread. A stalled Google endpoint must NEVER
     * hold an active database transaction or block an API request thread.
     */
    @Bean(name = "pushNotificationExecutor")
    Executor pushNotificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("PushNotif-");
        executor.setTaskDecorator(tenantAndMdcTaskDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(name = "menuExtractionExecutor")
    Executor menuExtractionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("MenuParser-");
        executor.setTaskDecorator(tenantAndMdcTaskDecorator());
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
        executor.setTaskDecorator(tenantAndMdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * Dedicated scheduler thread pool for all 12 @Scheduled background jobs.
     * Pool size expanded from 2 to 10 threads so slow external API calls or scraping
     * (e.g. FSSAI tracking, Easebuzz reconciliation) do not stall time-sensitive jobs
     * (e.g. daily closing sweep, webhook sweeps, token cleanup).
     */
    @Bean
    TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("KhanaScheduler-");
        scheduler.setErrorHandler(t ->
                org.slf4j.LoggerFactory.getLogger(AsyncConfig.class)
                        .error("Scheduled task failed: {}", t.getMessage(), t));
        scheduler.initialize();
        return scheduler;
    }
}
