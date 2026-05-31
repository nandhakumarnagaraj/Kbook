package com.khanabook.saas.service;

import com.khanabook.saas.entity.WebhookRetryJob;
import com.khanabook.saas.repository.WebhookRetryJobRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class WebhookRetryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetryService.class);
    private final WebhookRetryJobRepository retryJobRepository;
    private final RestTemplateBuilder restTemplateBuilder;

    private static final long[] BACKOFF_MS = {
        60_000, 120_000, 240_000, 480_000, 900_000, 1800_000
    };

    private Map<String, Function<String, Boolean>> webhookExecutors = new LinkedHashMap<>();

    public void registerWebhookExecutor(String webhookType, Function<String, Boolean> executor) {
        webhookExecutors.put(webhookType, executor);
    }

    @Transactional
    public WebhookRetryJob enqueue(String webhookType, String payload) {
        WebhookRetryJob job = new WebhookRetryJob();
        job.setWebhookType(webhookType);
        job.setPayload(payload);
        job.setStatus("PENDING");
        job.setAttemptCount(0);
        job.setMaxAttempts(BACKOFF_MS.length);
        job.setNextAttemptAt(System.currentTimeMillis());
        long now = System.currentTimeMillis();
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        return retryJobRepository.save(job);
    }

    @Transactional
    public void markSuccess(Long jobId) {
        retryJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus("COMPLETED");
            job.setUpdatedAt(System.currentTimeMillis());
            retryJobRepository.save(job);
        });
    }

    @Transactional
    public void markFailed(Long jobId, String error) {
        retryJobRepository.findById(jobId).ifPresent(job -> {
            int nextAttempt = job.getAttemptCount() + 1;
            job.setAttemptCount(nextAttempt);
            job.setLastError(error);
            if (nextAttempt >= job.getMaxAttempts()) {
                job.setStatus("DEAD_LETTER");
            } else {
                job.setNextAttemptAt(System.currentTimeMillis() + BACKOFF_MS[nextAttempt - 1]);
            }
            job.setUpdatedAt(System.currentTimeMillis());
            retryJobRepository.save(job);
        });
    }

    @Scheduled(fixedDelay = 30000)
    public void processPendingRetries() {
        long now = System.currentTimeMillis();
        List<WebhookRetryJob> pending = retryJobRepository.findByStatusAndNextAttemptAtLessThanEqual("PENDING", now);
        if (pending.isEmpty()) return;
        log.info("Processing {} pending webhook retry jobs", pending.size());
        for (WebhookRetryJob job : pending) {
            log.info("Retrying webhook jobId={} type={} attempt={}/{}", job.getId(), job.getWebhookType(), job.getAttemptCount() + 1, job.getMaxAttempts());
            executeRetry(job);
        }
    }

    private void executeRetry(WebhookRetryJob job) {
        Function<String, Boolean> executor = webhookExecutors.get(job.getWebhookType());
        if (executor == null) {
            log.warn("No executor registered for webhook type: {}", job.getWebhookType());
            markFailed(job.getId(), "No executor registered for type: " + job.getWebhookType());
            return;
        }
        try {
            boolean success = executor.apply(job.getPayload());
            if (success) {
                markSuccess(job.getId());
                log.info("Webhook retry succeeded: jobId={}", job.getId());
            } else {
                markFailed(job.getId(), "Executor returned false");
                log.warn("Webhook retry failed: jobId={}", job.getId());
            }
        } catch (Exception e) {
            markFailed(job.getId(), e.getMessage());
            log.error("Webhook retry error: jobId={}", job.getId(), e);
        }
    }

    public Map<String, Object> getHealthSummary() {
        List<Object[]> statusCounts = retryJobRepository.countByStatusGrouped();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : statusCounts) {
            byStatus.put((String) row[0], ((Number) row[1]).longValue());
        }
        long pending = retryJobRepository.countByStatus("PENDING");
        long deadLetter = retryJobRepository.countByStatus("DEAD_LETTER");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("byStatus", byStatus);
        result.put("pendingRetries", pending);
        result.put("deadLetterCount", deadLetter);
        result.put("totalJobs", retryJobRepository.count());
        return result;
    }

    public List<Map<String, Object>> getDeadLetterJobs() {
        List<WebhookRetryJob> jobs = retryJobRepository.findByStatusOrderByCreatedAtDesc("DEAD_LETTER");
        return jobs.stream().map(j -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", j.getId());
            m.put("webhookType", j.getWebhookType());
            m.put("attemptCount", j.getAttemptCount());
            m.put("lastError", j.getLastError());
            m.put("createdAt", j.getCreatedAt());
            return m;
        }).toList();
    }

    @Transactional
    public void replayDeadLetter(Long jobId) {
        retryJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus("PENDING");
            job.setAttemptCount(0);
            job.setNextAttemptAt(System.currentTimeMillis());
            job.setUpdatedAt(System.currentTimeMillis());
            retryJobRepository.save(job);
        });
    }
}