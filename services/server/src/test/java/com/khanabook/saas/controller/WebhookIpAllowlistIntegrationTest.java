package com.khanabook.saas.controller;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.feature.payments.service.EasebuzzWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the allowlist is enforced by the real servlet filter chain, not only by the
 * filter's own unit tests: an off-list caller must never reach the webhook handler.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "easebuzz.webhook.allowed-ips=10.0.0.0/8")
class WebhookIpAllowlistIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EasebuzzWebhookService webhookService;

    @Test
    void rejectsWebhookFromOutsideTheAllowlistBeforeItReachesTheHandler() throws Exception {
        mockMvc.perform(post("/payments/easebuzz/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"txnid\":\"T1\",\"status\":\"success\"}"))
                // MockMvc defaults to 127.0.0.1, outside 10.0.0.0/8.
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("webhook_source_not_allowed"));

        verifyNoInteractions(webhookService);
    }

    @Test
    void rejectsRefundWebhookFromOutsideTheAllowlist() throws Exception {
        mockMvc.perform(post("/payments/easebuzz/refund/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"txnid\":\"T2\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptsWebhookFromAnAllowlistedAddress() throws Exception {
        when(webhookService.handlePaymentWebhook(anyMap())).thenReturn(Map.of("status", "ok"));

        mockMvc.perform(post("/payments/easebuzz/webhook")
                        .with(request -> {
                            request.setRemoteAddr("10.1.2.3");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"txnid\":\"T3\",\"status\":\"success\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void doesNotFilterAuthenticatedNonWebhookPaymentRoutes() throws Exception {
        // /status/... is a normal authenticated route; the allowlist must not touch it.
        mockMvc.perform(post("/payments/easebuzz/verify/999999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
