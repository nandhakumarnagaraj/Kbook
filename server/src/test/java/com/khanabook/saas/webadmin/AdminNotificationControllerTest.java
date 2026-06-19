package com.khanabook.saas.webadmin;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.service.PushNotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminNotificationControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.khanabook.saas.repository.UserRepository userRepository;

    @Autowired
    private com.khanabook.saas.repository.RestaurantProfileRepository restaurantProfileRepository;

    @MockBean
    private PushNotificationService pushNotificationService;

    @org.junit.jupiter.api.BeforeEach
    void cleanDb() {
        userRepository.deleteAll();
        restaurantProfileRepository.deleteAll();
    }

    @Test
    void testSendCustomNotificationAsAdmin() throws Exception {
        String token = persistUserAndGetToken("admin@test.com", 1L, UserRole.KBOOK_ADMIN);

        mockMvc.perform(post("/admin/notifications/send")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":9501,\"title\":\"Custom Title\",\"message\":\"Custom Message\",\"type\":\"marketing\",\"referenceId\":\"ref-1\",\"referenceType\":\"campaign\",\"amount\":150.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Custom notification triggered"));

        Mockito.verify(pushNotificationService).pushToRestaurant(
                eq(9501L),
                eq("Custom Title"),
                eq("Custom Message"),
                eq("marketing"),
                eq("ref-1"),
                eq("campaign"),
                eq(new BigDecimal("150.0"))
        );
    }

    @Test
    void testSendCustomNotificationAsOwnerForbidden() throws Exception {
        String token = persistUserAndGetToken("owner@test.com", 9501L, UserRole.OWNER);

        mockMvc.perform(post("/admin/notifications/send")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":9501,\"title\":\"Custom Title\",\"message\":\"Custom Message\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testSendCustomNotificationMissingRestaurantId() throws Exception {
        String token = persistUserAndGetToken("admin-missing@test.com", 1L, UserRole.KBOOK_ADMIN);

        mockMvc.perform(post("/admin/notifications/send")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Custom Title\",\"message\":\"Custom Message\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("restaurantId is required"));
    }
}
