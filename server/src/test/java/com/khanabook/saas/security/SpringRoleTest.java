package com.khanabook.saas.security;

import com.khanabook.saas.BaseIntegrationTest;
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
    private JwtUtility jwtUtility;

    @Test
    void givenOwnerJwtWithTenant1_whenPostSyncBillsWithTenant1_then200() throws Exception {
        String token = jwtUtility.generateToken("owner@test.com", 1L, "OWNER");
        
        mockMvc.perform(post("/sync/bills/push")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
                .andExpect(status().isOk());
    }

    @Test
    void givenOwnerJwtWithTenant1_whenPostSyncBillsWithTenant2_then403() throws Exception {
        // GenericSyncService throws AccessDeniedException when restaurantId in payload (passed via controller from TenantContext)
        // doesn't match if it's not a KBOOK_ADMIN.
        // Wait, the controller passes TenantContext.getCurrentTenant() which is 1.
        // If the payload contains 2, GenericSyncService.handlePushSync will check:
        // if (!isKbookAdmin && !record.getRestaurantId().equals(tenantId)) { throw new AccessDeniedException(...) }
        
        String token = jwtUtility.generateToken("owner@test.com", 1L, "OWNER");
        
        // Payload with mismatched restaurantId
        String payload = "[{\"localId\":1, \"restaurantId\":2, \"totalAmount\":\"100.00\"}]";
        
        mockMvc.perform(post("/sync/bills/push")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenOwnerJwt_whenGetAdminPath_then403() throws Exception {
        String token = jwtUtility.generateToken("owner@test.com", 1L, "OWNER");
        
        mockMvc.perform(get("/admin/anything")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenKbookAdminJwt_whenPostSyncBillsWithAnyTenant_then200() throws Exception {
        // KBOOK_ADMIN has null restaurantId in token usually, but can push for any
        String token = jwtUtility.generateToken("admin@test.com", null, "KBOOK_ADMIN");
        
        String payload = "[{\"localId\":1, \"restaurantId\":99, \"totalAmount\":\"100.00\"}]";
        
        mockMvc.perform(post("/sync/bills/push")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void givenKbookAdminJwt_whenGetAdminPath_then200() throws Exception {
        String token = jwtUtility.generateToken("admin@test.com", null, "KBOOK_ADMIN");
        
        mockMvc.perform(get("/sync/master/admin/test")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void givenKbookAdminJwt_whenGetMasterPullWithTenant99_then200() throws Exception {
        String token = jwtUtility.generateToken("admin@test.com", null, "KBOOK_ADMIN");
        
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
                .andExpect(status().isForbidden());
    }

    @Test
    void givenStaffJwt_whenAnySync_then403() throws Exception {
        // STAFF is not in our UserRole enum anymore, but if someone crafts a JWT with it:
        String token = jwtUtility.generateToken("staff@test.com", 1L, "STAFF");
        
        mockMvc.perform(get("/sync/bills/pull")
                .header("Authorization", "Bearer " + token)
                .param("lastSyncTimestamp", "0")
                .param("deviceId", "test-device"))
                .andExpect(status().isForbidden());
    }
}
