package com.khanabook.saas.controller;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.payment.dto.SaveRestaurantPaymentConfigRequest;
import com.khanabook.saas.payment.entity.RestaurantPaymentConfig;
import com.khanabook.saas.payment.entity.PaymentGateway;
import com.khanabook.saas.payment.repository.RestaurantPaymentConfigRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class RestaurantPaymentConfigControllerTest extends BaseIntegrationTest {

    private static final Long RESTAURANT_ID = 5001L;
    private static final Long OTHER_RESTAURANT_ID = 5002L;

    @Autowired private MockMvc mockMvc;
    @Autowired private RestaurantPaymentConfigRepository repository;
    @Autowired private RestaurantProfileRepository restaurantProfileRepository;
    @Autowired private UserRepository userRepository;

    private String ownerToken;
    private String adminToken;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        userRepository.deleteAll();
        restaurantProfileRepository.deleteAll();

        ownerToken = persistUserAndGetToken("owner@test.com", RESTAURANT_ID, UserRole.OWNER);
        adminToken = persistUserAndGetToken("admin@test.com", RESTAURANT_ID, UserRole.KBOOK_ADMIN);
        persistUser("owner-other@test.com", OTHER_RESTAURANT_ID, UserRole.OWNER);

        savePaymentConfig(RESTAURANT_ID, "test-merchant", "test-salt", "TEST", true);
    }

    @Test
    void toggleActive_withOwner_togglesOwnRestaurantConfig() throws Exception {
        mockMvc.perform(patch("/restaurants/payment-config/easebuzz/toggle?enabled=false")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void toggleActive_withOwnerAndRestaurantIdParam_ignoresRestaurantIdParam() throws Exception {
        mockMvc.perform(patch("/restaurants/payment-config/easebuzz/toggle?enabled=false&restaurantId=" + OTHER_RESTAURANT_ID)
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void toggleActive_withAdminAndRestaurantIdParam_acceptsOverride() throws Exception {
        savePaymentConfig(OTHER_RESTAURANT_ID, "other-merchant", "other-salt", "PROD", true);

        mockMvc.perform(patch("/restaurants/payment-config/easebuzz/toggle?enabled=false&restaurantId=" + OTHER_RESTAURANT_ID)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void toggleActive_nonOwnerNonAdmin_returnsForbidden() throws Exception {
        String managerToken = persistUserAndGetToken("manager@test.com", RESTAURANT_ID, UserRole.MANAGER);

        mockMvc.perform(patch("/restaurants/payment-config/easebuzz/toggle?enabled=false")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void toggleActive_configNotFound_returns400() throws Exception {
        repository.deleteAll();

        mockMvc.perform(patch("/restaurants/payment-config/easebuzz/toggle?enabled=false")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void toggleActive_alreadyInDesiredState_returnsCurrentState() throws Exception {
        mockMvc.perform(patch("/restaurants/payment-config/easebuzz/toggle?enabled=true")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        RestaurantPaymentConfig config = repository.findByRestaurantIdAndGatewayName(RESTAURANT_ID, PaymentGateway.EASEBUZZ).orElseThrow();
        long updatedAtAfterNoOp = config.getUpdatedAt();

        mockMvc.perform(patch("/restaurants/payment-config/easebuzz/toggle?enabled=true")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        config = repository.findByRestaurantIdAndGatewayName(RESTAURANT_ID, PaymentGateway.EASEBUZZ).orElseThrow();
        assert config.getUpdatedAt() == updatedAtAfterNoOp : "updatedAt should not change on no-op toggle";
    }

    private void savePaymentConfig(Long restaurantId, String merchantKey, String salt, String env, boolean active) {
        RestaurantPaymentConfig config = new RestaurantPaymentConfig();
        config.setRestaurantId(restaurantId);
        config.setGatewayName(PaymentGateway.EASEBUZZ);
        config.setMerchantKey(merchantKey);
        config.setEncryptedSalt(salt);
        config.setEnvironment(env);
        config.setIsActive(active);
        config.setCreatedAt(System.currentTimeMillis());
        config.setUpdatedAt(System.currentTimeMillis());
        repository.save(config);
    }
}
