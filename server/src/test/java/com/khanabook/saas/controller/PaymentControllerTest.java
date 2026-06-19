package com.khanabook.saas.controller;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.service.EasebuzzPaymentService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PaymentControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EasebuzzPaymentService paymentService;

    @org.junit.jupiter.api.BeforeEach
    void cleanDb() {
        userRepository.deleteAll();
        restaurantProfileRepository.deleteAll();
    }

    @Test
    void testCreateFssaiOrderSuccess() throws Exception {
        String token = persistUserAndGetToken("9000000001", 9501L, UserRole.OWNER);

        Mockito.when(paymentService.createFssaiRenewalOrder(anyInt(), anyString(), anyLong()))
                .thenReturn(Map.of("status", "success", "access_token", "dummy-access-token", "txnid", "KBF9501TEST"));

        mockMvc.perform(post("/payments/easebuzz/create-fssai-order")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"years\":2,\"fssaiNumber\":\"13023001000255\",\"restaurantId\":9501}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.access_token").value("dummy-access-token"));
    }

    @Test
    void testCreateFssaiOrderMissingParams() throws Exception {
        String token = persistUserAndGetToken("9000000001", 9501L, UserRole.OWNER);

        mockMvc.perform(post("/payments/easebuzz/create-fssai-order")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"years\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("failure"))
                .andExpect(jsonPath("$.error").value("years, fssaiNumber and restaurantId are required"));
    }
}
