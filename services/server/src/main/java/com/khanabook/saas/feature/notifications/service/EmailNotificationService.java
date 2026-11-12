package com.khanabook.saas.feature.notifications.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username:noreply@khanabook.com}")
    private String fromEmail;

    @Value("${khanabook.email.enabled:false}")
    private boolean emailEnabled;

    @Async
    public void sendRefundConfirmation(String toEmail, String customerName, String orderCode,
                                        BigDecimal refundAmount, String reason) {
        if (!emailEnabled || toEmail == null || toEmail.isBlank()) {
            log.debug("Email disabled or no recipient for refund confirmation");
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("customerName", customerName);
            ctx.setVariable("orderCode", orderCode);
            ctx.setVariable("refundAmount", refundAmount);
            ctx.setVariable("reason", reason);
            ctx.setVariable("supportEmail", "support@khanabook.com");

            String htmlContent = templateEngine.process("refund-confirmation", ctx);
            sendHtmlEmail(toEmail, "Refund Confirmation - " + orderCode, htmlContent);
            log.info("Refund confirmation email sent to {} for order {}", toEmail, orderCode);
        } catch (Exception e) {
            log.error("Failed to send refund confirmation email to {}", toEmail, e);
        }
    }

    @Async
    public void sendInstantSettlementNotification(String toEmail, String shopName, BigDecimal amount,
                                                   BigDecimal fee, BigDecimal netPayout) {
        if (!emailEnabled || toEmail == null || toEmail.isBlank()) {
            log.debug("Email disabled or no recipient for settlement notification");
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("shopName", shopName);
            ctx.setVariable("amount", amount);
            ctx.setVariable("fee", fee);
            ctx.setVariable("netPayout", netPayout);
            ctx.setVariable("settlementDate", java.time.LocalDate.now());
            ctx.setVariable("supportEmail", "support@khanabook.com");

            String htmlContent = templateEngine.process("settlement-notification", ctx);
            sendHtmlEmail(toEmail, "Instant Settlement Processed - " + shopName, htmlContent);
            log.info("Settlement notification email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send settlement notification email to {}", toEmail, e);
        }
    }

    @Async
    public void sendOnboardingWelcome(String toEmail, String shopName, String ownerName) {
        if (!emailEnabled || toEmail == null || toEmail.isBlank()) {
            log.debug("Email disabled or no recipient for onboarding welcome");
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("ownerName", ownerName);
            ctx.setVariable("shopName", shopName);
            ctx.setVariable("loginUrl", "https://admin.khanabook.com/login");
            ctx.setVariable("docsUrl", "https://docs.khanabook.com");
            ctx.setVariable("supportEmail", "support@khanabook.com");

            String htmlContent = templateEngine.process("onboarding-welcome", ctx);
            sendHtmlEmail(toEmail, "Welcome to KhanaBook - " + shopName, htmlContent);
            log.info("Onboarding welcome email sent to {} for shop {}", toEmail, shopName);
        } catch (Exception e) {
            log.error("Failed to send onboarding welcome email to {}", toEmail, e);
        }
    }

    @Async
    public void sendChargebackAlert(String toEmail, String shopName, String orderCode,
                                     BigDecimal amount, String reasonCode) {
        if (!emailEnabled || toEmail == null || toEmail.isBlank()) {
            log.debug("Email disabled or no recipient for chargeback alert");
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("shopName", shopName);
            ctx.setVariable("orderCode", orderCode);
            ctx.setVariable("amount", amount);
            ctx.setVariable("reasonCode", reasonCode);
            ctx.setVariable("dashboardUrl", "https://admin.khanabook.com/business/chargebacks");

            String htmlContent = templateEngine.process("chargeback-alert", ctx);
            sendHtmlEmail(toEmail, "Chargeback Alert - " + shopName, htmlContent);
            log.info("Chargeback alert email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send chargeback alert email to {}", toEmail, e);
        }
    }

    public void sendVerificationOtp(String toEmail, String otp) {
        if (!emailEnabled) {
            log.warn("Email sending disabled (khanabook.email.enabled=false). Generated email OTP for {}: {}", toEmail, otp);
            return;
        }
        try {
            String subject = "KhanaBook Verification Code: " + otp;
            String htmlContent = "<div style=\"font-family: sans-serif; padding: 20px; color: #1e293b;\">"
                    + "<h2 style=\"color: #0f172a;\">Verify your email address</h2>"
                    + "<p>You requested to update your KhanaBook account email. Use the code below to complete verification:</p>"
                    + "<div style=\"background-color: #f1f5f9; padding: 16px; border-radius: 8px; font-size: 28px; font-weight: bold; letter-spacing: 4px; text-align: center; margin: 24px 0; color: #0284c7;\">"
                    + otp + "</div>"
                    + "<p style=\"color: #64748b; font-size: 14px;\">This code is valid for 10 minutes. If you did not make this request, you can safely ignore this email.</p>"
                    + "<hr style=\"border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;\" />"
                    + "<p style=\"font-size: 12px; color: #94a3b8;\">KhanaBook POS &bull; Smart Restaurant POS &amp; Management System</p>"
                    + "</div>";
            sendHtmlEmail(toEmail, subject, htmlContent);
            log.info("Verification OTP email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification OTP email to {}", toEmail, e);
            throw new IllegalStateException("Failed to send verification email. Please try again.", e);
        }
    }

    public void sendDailyClosingReport(String toEmail, String subject, String htmlContent) throws MessagingException {
        if (!emailEnabled) {
            log.info("Email disabled (khanabook.email.enabled=false). Daily closing report suppressed for {}", toEmail);
            return;
        }
        sendHtmlEmail(toEmail, subject, htmlContent);
        log.info("Daily closing report email sent to {}", toEmail);
    }

    public void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        try {
            helper.setFrom(fromEmail, "KhanaBook POS");
        } catch (Exception e) {
            helper.setFrom(fromEmail);
        }
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        mailSender.send(message);
    }
}
