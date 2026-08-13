package com.khanabook.saas.service;

import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ComplianceAlertService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceAlertService.class);
    
    private final RestaurantProfileRepository restaurantProfileRepository;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${whatsapp.meta.access-token:}")
    private String metaAccessToken;

    @Value("${whatsapp.meta.phone-number-id:}")
    private String phoneNumberId;

    public ComplianceAlertService(RestaurantProfileRepository restaurantProfileRepository) {
        this.restaurantProfileRepository = restaurantProfileRepository;
    }

    @Scheduled(cron = "0 0 9 * * *") // Daily at 9 AM
    @Transactional(readOnly = true)
    public void runDailyAlerts() {
        log.info("Running daily compliance expiry alerts check...");
        sendAlertsForDate(LocalDate.now());
    }

    public void sendAlertsForDate(LocalDate today) {
        List<RestaurantProfile> profiles = restaurantProfileRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        for (RestaurantProfile profile : profiles) {
            checkAndAlert(profile, today);
        }
    }

    private void checkAndAlert(RestaurantProfile profile, LocalDate today) {
        String whatsappNumber = profile.getWhatsappNumber();
        if (whatsappNumber == null || whatsappNumber.isBlank()) {
            return;
        }

        // Check FSSAI
        if (profile.getFssaiExpiryDate() != null) {
            long daysToExpiry = ChronoUnit.DAYS.between(today, profile.getFssaiExpiryDate());
            if (daysToExpiry == 30 || daysToExpiry == 15 || daysToExpiry == 7) {
                sendWhatsappAlert(profile.getShopName(), whatsappNumber, "FSSAI License", profile.getFssaiNumber(), profile.getFssaiExpiryDate(), daysToExpiry);
            }
        }

        // Check GST
        if (profile.getGstExpiryDate() != null) {
            long daysToExpiry = ChronoUnit.DAYS.between(today, profile.getGstExpiryDate());
            if (daysToExpiry == 30 || daysToExpiry == 15 || daysToExpiry == 7) {
                sendWhatsappAlert(profile.getShopName(), whatsappNumber, "GSTIN Registration", profile.getGstin(), profile.getGstExpiryDate(), daysToExpiry);
            }
        }
    }

    private void sendWhatsappAlert(String shopName, String phoneNumber, String documentType, String documentNumber, LocalDate expiryDate, long daysToExpiry) {
        if (metaAccessToken == null || metaAccessToken.isBlank() || phoneNumberId == null || phoneNumberId.isBlank()) {
            log.warn("WhatsApp compliance alert config missing. Skipping alert for shop={} phone={}", shopName, phoneNumber);
            return;
        }

        String formattedPhoneNumber = formatWhatsappPhoneNumber(phoneNumber);
        String templateName = "compliance_expiry_alert"; 
        
        String body = """
                {
                  "messaging_product": "whatsapp",
                  "to": "%s",
                  "type": "template",
                  "template": {
                    "name": "%s",
                    "language": { "code": "en" },
                    "components": [
                      {
                        "type": "body",
                        "parameters": [
                          { "type": "text", "text": "%s" },
                          { "type": "text", "text": "%s" },
                          { "type": "text", "text": "%s" },
                          { "type": "text", "text": "%s" }
                        ]
                      }
                    ]
                  }
                }
                """.formatted(
                        escapeJson(formattedPhoneNumber),
                        escapeJson(templateName),
                        escapeJson(shopName),
                        escapeJson(documentType),
                        escapeJson(expiryDate.toString()),
                        escapeJson(String.valueOf(daysToExpiry))
                );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://graph.facebook.com/v17.0/" + phoneNumberId + "/messages"))
                .timeout(Duration.ofSeconds(30))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + metaAccessToken)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.error("WhatsApp compliance alert send failed for shop={} status={}", shopName, response.statusCode());
            } else {
                log.info("WhatsApp compliance alert sent successfully for shop={} type={}", shopName, documentType);
            }
        } catch (Exception e) {
            log.error("Failed to send WhatsApp compliance alert for shop={}", shopName, e);
        }
    }

    private String formatWhatsappPhoneNumber(String phoneNumber) {
        String digits = phoneNumber == null ? "" : phoneNumber.replaceAll("[^0-9]", "");
        if (digits.length() == 10) {
            if (digits.startsWith("91")) {
                return digits;
            }
            return "91" + digits;
        }
        if (digits.startsWith("91") && digits.length() == 12) {
            return digits;
        }
        return digits;
    }

    private String escapeJson(String input) {
        return input == null ? "" : input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
