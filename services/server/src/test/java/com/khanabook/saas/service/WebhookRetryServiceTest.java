package com.khanabook.saas.service;

import com.khanabook.saas.feature.payments.data.WebhookRetryJob;
import com.khanabook.saas.feature.payments.data.WebhookRetryJobRepository;
import com.khanabook.saas.feature.payments.service.WebhookRetryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookRetryServiceTest {
    @Mock private WebhookRetryJobRepository repository;
    @Mock private RestTemplateBuilder restTemplateBuilder;
    @InjectMocks private WebhookRetryService service;

    @Test
    void enqueueAtPersistsRequestedTimeAndIdempotencyKey() {
        long scheduledAt = 1_900_000_000_000L;
        when(repository.findByJobKey("DELAYED_REFUND:10:20")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WebhookRetryJob job = service.enqueueAt("DELAYED_REFUND", "{}", scheduledAt,
                "DELAYED_REFUND:10:20");

        assertThat(job.getStatus()).isEqualTo("PENDING");
        assertThat(job.getNextAttemptAt()).isEqualTo(scheduledAt);
        assertThat(job.getJobKey()).isEqualTo("DELAYED_REFUND:10:20");
        verify(repository).save(job);
    }

    @Test
    void enqueueAtReturnsExistingJobForSameKey() {
        WebhookRetryJob existing = new WebhookRetryJob();
        existing.setId(44L);
        existing.setJobKey("DELAYED_REFUND:10:20");
        when(repository.findByJobKey("DELAYED_REFUND:10:20")).thenReturn(Optional.of(existing));

        WebhookRetryJob result = service.enqueueAt("DELAYED_REFUND", "new", 123L,
                "DELAYED_REFUND:10:20");

        assertThat(result).isSameAs(existing);
        verify(repository, never()).save(any());
    }
}
