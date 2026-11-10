package com.khanabook.saas.feature.reports.service;

import com.khanabook.saas.core.utility.BusinessDay;
import com.khanabook.saas.feature.notifications.data.EmailOutbox;
import com.khanabook.saas.feature.notifications.data.EmailOutboxRepository;
import com.khanabook.saas.feature.notifications.service.EmailNotificationService;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyClosingEmailScheduler {

    private final DailyClosingReportService dailyClosingReportService;
    private final RestaurantProfileRepository restaurantProfileRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailNotificationService emailNotificationService;

    /**
     * Checks all active restaurants periodically. When a restaurant's trading hours for the day have
     * completed (current local time has reached or passed closing time), automatically generates and
     * sends the End of Day closing report to the shop owner's email.
     */
    @Scheduled(cron = "${khanabook.email.daily-closing.cron:0 0/15 * * * *}")
    public void runDailyClosingEmailSweep() {
        log.debug("Starting daily closing email sweep for active restaurants");
        List<RestaurantProfile> restaurants = restaurantProfileRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();

        for (RestaurantProfile profile : restaurants) {
            try {
                processRestaurantClosing(profile);
            } catch (Exception e) {
                log.error("Error processing daily closing email for restaurant {}: {}",
                        profile.getRestaurantId(), e.getMessage(), e);
            }
        }
    }

    private void processRestaurantClosing(RestaurantProfile profile) {
        String recipient = profile.getDailyClosingEmailRecipients();
        if (recipient == null || recipient.isBlank()) {
            recipient = profile.getEmail();
        }
        if (recipient == null || recipient.isBlank()) {
            return;
        }

        ZoneId zone = BusinessDay.zoneOf(profile.getTimezone());
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalTime localTime = now.toLocalTime();

        LocalTime openingTime = profile.getOpeningTime() != null ? profile.getOpeningTime() : LocalTime.of(8, 0);
        LocalTime closingTime = profile.getClosingTime() != null ? profile.getClosingTime() : LocalTime.of(23, 0);
        LocalTime cutoffTime = BusinessDay.parseCutoff(
                profile.getBusinessDayCutoffTime() != null ? profile.getBusinessDayCutoffTime().toString() : "00:00"
        );

        String currentBusinessDate = BusinessDay.businessDate(
                now.toInstant().toEpochMilli(), zone, cutoffTime, profile.getCutoffEffectiveFrom(),
                openingTime, closingTime
        );

        // Determine if current local time is past closing time:
        // Case A (Daytime trade, e.g. 08:00 - 23:00): Closed after 23:00 or before next opening (08:00).
        // Case B (Night trade crossing midnight, e.g. 18:00 - 02:00): Closed between closing (02:00) and opening (18:00).
        boolean pastClosing;
        if (closingTime.isAfter(openingTime)) {
            pastClosing = !localTime.isBefore(closingTime) || localTime.isBefore(openingTime);
        } else {
            pastClosing = !localTime.isBefore(closingTime) && localTime.isBefore(openingTime);
        }

        boolean beforeCutoff = !LocalTime.MIDNIGHT.equals(cutoffTime) && localTime.isBefore(cutoffTime);

        if (pastClosing || beforeCutoff) {
            dailyClosingReportService.generateAndSendReport(profile.getRestaurantId(), currentBusinessDate, false);
        }

        // Safety sweep for previous day in case server was restarting during cutoff/close
        ZonedDateTime yesterday = now.minusDays(1);
        String prevBusinessDate = BusinessDay.businessDate(
                yesterday.toInstant().toEpochMilli(), zone, cutoffTime, profile.getCutoffEffectiveFrom(),
                openingTime, closingTime
        );
        if (!prevBusinessDate.equals(currentBusinessDate)) {
            dailyClosingReportService.generateAndSendReport(profile.getRestaurantId(), prevBusinessDate, false);
        }
    }

    /**
     * Outbox retry processor: retries failed or pending emails with exponential backoff.
     */
    @Scheduled(fixedDelayString = "${khanabook.email.outbox-retry-delay:60000}")
    public void processOutboxRetries() {
        long now = System.currentTimeMillis();
        List<EmailOutbox> pending = emailOutboxRepository.findPendingEmails(
                List.of(EmailOutbox.STATUS_PENDING, EmailOutbox.STATUS_FAILED), now
        );

        for (EmailOutbox item : pending) {
            if (item.getAttemptCount() >= item.getMaxAttempts()) {
                item.setStatus(EmailOutbox.STATUS_DEAD_LETTER);
                item.setUpdatedAt(now);
                emailOutboxRepository.save(item);
                continue;
            }

            try {
                emailNotificationService.sendDailyClosingReport(item.getRecipient(), item.getSubject(), item.getHtmlBody());
                item.setStatus(EmailOutbox.STATUS_SENT);
                item.setSentAt(System.currentTimeMillis());
                item.setAttemptCount(item.getAttemptCount() + 1);
                item.setUpdatedAt(System.currentTimeMillis());
                emailOutboxRepository.save(item);
                log.info("Successfully delivered queued email outbox id={} to {}", item.getId(), item.getRecipient());
            } catch (Exception e) {
                int attempts = item.getAttemptCount() + 1;
                item.setAttemptCount(attempts);
                item.setLastError(e.getMessage());
                item.setUpdatedAt(System.currentTimeMillis());
                if (attempts >= item.getMaxAttempts()) {
                    item.setStatus(EmailOutbox.STATUS_DEAD_LETTER);
                } else {
                    item.setStatus(EmailOutbox.STATUS_FAILED);
                    long backoff = (long) Math.pow(2, attempts) * 60_000L;
                    item.setNextAttemptAt(System.currentTimeMillis() + backoff);
                }
                emailOutboxRepository.save(item);
                log.warn("Failed to deliver queued email outbox id={}: {}", item.getId(), e.getMessage());
            }
        }
    }
}
