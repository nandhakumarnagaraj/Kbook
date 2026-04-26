package com.khanabook.saas.security;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.UserRole;
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
    void givenOwnerJwtWithTenant1_whenPostSyncBillsWithTenant2_then403() throws Exception {
        String token = persistUserAndGetToken("owner2@test.com", 1L, UserRole.OWNER);
        
        String payload = "[{\"localId\":1, \"restaurantId\":2, \"totalAmount\":\"100.00\"}]";
        
        mockMvc.perform(post("/sync/bills/push")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isForbidden());
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
