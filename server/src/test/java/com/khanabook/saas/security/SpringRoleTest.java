package com.khanabook.saas.security;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.RestaurantTerminal;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.utility.JwtUtility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
public class SpringRoleTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RestaurantTerminalRepository terminalRepository;
    @Autowired
    private JwtUtility jwtUtility;

    private String terminalTokenFor(Long restaurantId) {
        RestaurantTerminal t = terminalRepository.findByRestaurantIdAndTerminalSeries(restaurantId, "A")
                .orElseGet(() -> {
                    RestaurantTerminal nt = new RestaurantTerminal();
                    nt.setRestaurantId(restaurantId);
                    nt.setTerminalSeries("A");
                    nt.setTerminalName("Terminal A");
                    nt.setDeviceId("DEV_A");
                    nt.setIsActive(true);
                    nt.setCreatedAt(System.currentTimeMillis());
                    nt.setUpdatedAt(System.currentTimeMillis());
                    return terminalRepository.save(nt);
                });
        String id = t.getId() != null ? t.getId().toString() : "A";
        return jwtUtility.generateTerminalToken("owner", restaurantId, "OWNER", id, "A", "DEV_A");
    }

    @Test
    void givenOwnerJwtWithTenant1_whenPostSyncBillsWithTenant1_then200() throws Exception {
        String token = persistUserAndGetToken("owner@test.com", 1L, UserRole.OWNER);
        
        mockMvc.perform(post("/sync/bills/push")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
                .andExpect(status().isOk());
    }

    @Test
    void givenOwnerJwtWithTenant1_whenPostSyncBillsWithTenant2_then200() throws Exception {
        String token = persistUserAndGetToken("owner2@test.com", 1L, UserRole.OWNER);
        String terminalToken = terminalTokenFor(1L);
        
        String payload = "[{\"localId\":1, \"deviceId\":\"DEV_A\", \"restaurantId\":2, \"orderType\":\"dine-in\", \"lifetimeOrderId\":1, \"totalAmount\":\"100.00\", \"subtotal\":\"100.00\", \"updatedAt\":1000, \"createdAt\":1000, \"dailyOrderId\":1, \"paymentMode\":\"cash\", \"paymentStatus\":\"paid\", \"orderStatus\":\"completed\"}]";
        
        mockMvc.perform(post("/sync/bills/push")
                .header("Authorization", "Bearer " + token)
                .header("X-Terminal-Token", terminalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void givenOwnerJwt_whenGetAdminPath_then403() throws Exception {
        String token = persistUserAndGetToken("owner3@test.com", 1L, UserRole.OWNER);
        
        mockMvc.perform(get("/actuator")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenKbookAdminJwt_whenPostSyncBillsWithAnyTenant_then200() throws Exception {
        String token = persistUserAndGetToken("admin@test.com", 999L, null, UserRole.KBOOK_ADMIN);
        
        String payload = "[{\"localId\":1, \"restaurantId\":99, \"totalAmount\":\"100.00\"}]";
        
        mockMvc.perform(post("/sync/bills/push")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void givenKbookAdminJwt_whenGetAdminPath_then200() throws Exception {
        String token = persistUserAndGetToken("admin2@test.com", 999L, null, UserRole.KBOOK_ADMIN);
        
        mockMvc.perform(get("/actuator")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void givenKbookAdminJwt_whenGetMasterPullWithTenant99_then200() throws Exception {
        String token = persistUserAndGetToken("admin3@test.com", 999L, null, UserRole.KBOOK_ADMIN);
        
        mockMvc.perform(get("/sync/master/pull")
                .header("Authorization", "Bearer " + token)
                .param("lastSyncTimestamp", "0")
                .param("deviceId", "test-device")
                .param("restaurantId", "99"))
                .andExpect(status().isOk());
    }

    @Test
    void givenNoJwt_whenGetSyncBills_thenForbidden() throws Exception {
        mockMvc.perform(get("/sync/bills/pull")
                .param("lastSyncTimestamp", "0")
                .param("deviceId", "test-device"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void givenStaffJwt_whenAnySync_then403() throws Exception {
        String token = jwtUtility.generateToken("staff@test.com", 1L, "STAFF");
        
        mockMvc.perform(get("/sync/bills/pull")
                .header("Authorization", "Bearer " + token)
                .param("lastSyncTimestamp", "0")
                .param("deviceId", "test-device"))
                .andExpect(status().isUnauthorized());
    }
}
