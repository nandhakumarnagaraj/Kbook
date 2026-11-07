package com.khanabook.saas.feature.reports.service;

import com.khanabook.saas.feature.notifications.data.EmailOutbox;
import com.khanabook.saas.feature.notifications.data.EmailOutboxRepository;
import com.khanabook.saas.feature.notifications.service.EmailNotificationService;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyClosingEmailSchedulerTest {

    @Mock private DailyClosingReportService dailyClosingReportService;
    @Mock private RestaurantProfileRepository restaurantProfileRepository;
    @Mock private EmailOutboxRepository emailOutboxRepository;
    @Mock private EmailNotificationService emailNotificationService;

    private DailyClosingEmailScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DailyClosingEmailScheduler(
                dailyClosingReportService,
                restaurantProfileRepository,
                emailOutboxRepository,
                emailNotificationService
        );
    }

    @Test
    void runsSweepAndTriggersReportForActiveRestaurants() {
        RestaurantProfile profile = new RestaurantProfile();
        profile.setId(1L);
        profile.setRestaurantId(101L);
        profile.setShopName("Biryani Palace");
        profile.setEmail("owner@biryani.com");
        profile.setOpeningTime(LocalTime.of(8, 0));
        profile.setClosingTime(LocalTime.of(0, 1)); // 00:01 so local time is almost certainly past closing
        profile.setBusinessDayCutoffTime(LocalTime.of(0, 0));
        profile.setTimezone("Asia/Kolkata");

        when(restaurantProfileRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(profile));

        scheduler.runDailyClosingEmailSweep();

        verify(dailyClosingReportService, atLeastOnce())
                .generateAndSendReport(eq(101L), anyString(), eq(false));
    }

    @Test
    void retriesPendingOutboxEmails() throws Exception {
        EmailOutbox item = EmailOutbox.builder()
                .id(50L)
                .restaurantId(101L)
                .reportType(EmailOutbox.REPORT_TYPE_DAILY_EOD)
                .referenceKey("2026-11-06")
                .recipient("owner@biryani.com")
                .subject("Test Subject")
                .htmlBody("<p>Report</p>")
                .status(EmailOutbox.STATUS_PENDING)
                .attemptCount(0)
                .maxAttempts(5)
                .build();

        when(emailOutboxRepository.findPendingEmails(anyCollection(), anyLong()))
                .thenReturn(List.of(item));

        scheduler.processOutboxRetries();

        verify(emailNotificationService).sendDailyClosingReport(
                eq("owner@biryani.com"),
                eq("Test Subject"),
                eq("<p>Report</p>")
        );

        assertThat(item.getStatus()).isEqualTo(EmailOutbox.STATUS_SENT);
        assertThat(item.getAttemptCount()).isEqualTo(1);
        assertThat(item.getSentAt()).isNotNull();
        verify(emailOutboxRepository).save(item);
    }
}
