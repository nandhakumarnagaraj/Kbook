package com.khanabook.saas.feature.notifications.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the delivery branches of {@link EmailNotificationService#sendVerificationOtp}.
 *
 * WHY: the POS Email-OTP button used to fail with "Failed to send verification email" on any
 * server without MAIL_PASSWORD, because the email-enabled flag defaults to true while the
 * password defaults to empty. These tests keep that unconfigured case non-throwing, and keep a
 * real transport failure loud.
 */
class EmailNotificationServiceTest {

    private JavaMailSender mailSender;
    private EmailNotificationService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        service = new EmailNotificationService(mailSender, mock(TemplateEngine.class));
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@khanabook.com");
        ReflectionTestUtils.setField(service, "emailEnabled", true);
        ReflectionTestUtils.setField(service, "mailPassword", "app-password");
    }

    @Test
    void unconfiguredSmtp_logsCodeAndSkipsSendWithoutThrowing() {
        ReflectionTestUtils.setField(service, "mailPassword", "");

        assertThatNoException().isThrownBy(() -> service.sendVerificationOtp("owner@shop.in", "123456"));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void disabledEmail_logsCodeAndSkipsSendWithoutThrowing() {
        ReflectionTestUtils.setField(service, "emailEnabled", false);

        assertThatNoException().isThrownBy(() -> service.sendVerificationOtp("owner@shop.in", "123456"));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void configuredSmtp_sendsVerificationEmail() {
        service.sendVerificationOtp("owner@shop.in", "123456");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void configuredSmtpTransportFailure_stillThrows() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> service.sendVerificationOtp("owner@shop.in", "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to send verification email");
    }
}
