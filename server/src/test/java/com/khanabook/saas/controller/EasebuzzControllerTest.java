package com.khanabook.saas.controller;

import com.khanabook.saas.payment.service.EasebuzzPaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EasebuzzControllerTest {

    @Test
    void canonicalStatus_mapsKnownSuccessVariants() {
        assertThat(EasebuzzController.canonicalStatus("success")).isEqualTo("success");
        assertThat(EasebuzzController.canonicalStatus("SUCCESS")).isEqualTo("success");
        assertThat(EasebuzzController.canonicalStatus("successful")).isEqualTo("success");
        assertThat(EasebuzzController.canonicalStatus("captured")).isEqualTo("success");
    }

    @Test
    void canonicalStatus_mapsKnownFailureVariants() {
        assertThat(EasebuzzController.canonicalStatus("failure")).isEqualTo("failed");
        assertThat(EasebuzzController.canonicalStatus("failed")).isEqualTo("failed");
        assertThat(EasebuzzController.canonicalStatus("userCancelled")).isEqualTo("failed");
        assertThat(EasebuzzController.canonicalStatus("user_cancelled")).isEqualTo("failed");
        assertThat(EasebuzzController.canonicalStatus("dropped")).isEqualTo("failed");
        assertThat(EasebuzzController.canonicalStatus("bounced")).isEqualTo("failed");
    }

    @Test
    void canonicalStatus_unknownAndNullDefaultToPending() {
        assertThat(EasebuzzController.canonicalStatus(null)).isEqualTo("pending");
        assertThat(EasebuzzController.canonicalStatus("initiated")).isEqualTo("pending");
        assertThat(EasebuzzController.canonicalStatus("garbage")).isEqualTo("pending");
        assertThat(EasebuzzController.canonicalStatus("")).isEqualTo("pending");
    }

    @Test
    void sha512_isDeterministicAndHexEncoded() {
        String a = EasebuzzController.sha512("hello|world");
        String b = EasebuzzController.sha512("hello|world");
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSize(128).matches("[0-9a-f]+");
    }

    @Test
    void returnSuccess_acceptsPostAndProcessesCallback() throws Exception {
        EasebuzzPaymentService paymentService = mock(EasebuzzPaymentService.class);
        when(paymentService.processGatewayCallback(anyMap())).thenReturn(java.util.Map.of("ok", true));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new EasebuzzController(paymentService)).build();

        mockMvc.perform(post("/payments/easebuzz/return/success")
                        .param("txnid", "TXN1")
                        .param("status", "success"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("khanabook://payment/success")));

        verify(paymentService).processGatewayCallback(anyMap());
    }

    @Test
    void returnFailure_stillRendersHtmlWhenReconciliationFails() throws Exception {
        EasebuzzPaymentService paymentService = mock(EasebuzzPaymentService.class);
        when(paymentService.processGatewayCallback(anyMap())).thenThrow(new IllegalArgumentException("boom"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new EasebuzzController(paymentService)).build();

        mockMvc.perform(get("/payments/easebuzz/return/failure")
                        .param("txnid", "TXN2")
                        .param("status", "failure"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("khanabook://payment/failure")));

        verify(paymentService).processGatewayCallback(anyMap());
    }
}
