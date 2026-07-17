package com.khanabook.saas.security;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.RestaurantTerminal;
import com.khanabook.saas.entity.SecurityAuditEvent;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.repository.SecurityAuditLogRepository;
import com.khanabook.saas.service.TerminalManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
public class TerminalTokenSecurityTest extends BaseIntegrationTest {

    private static final AtomicLong ID_SEQ = new AtomicLong(9901L);

    @Autowired private MockMvc mockMvc;
    @Autowired private RestaurantTerminalRepository terminalRepository;
    @Autowired private TerminalManagementService terminalManagementService;
    @Autowired private SecurityAuditLogRepository auditRepository;

    private Long rid; // per-test restaurant
    private Long ridB; // second restaurant for cross-tenant tests

    @BeforeEach
    void initRestaurants() {
        rid = ID_SEQ.getAndIncrement();
        ridB = ID_SEQ.getAndIncrement();
        ensureUser("sec-" + rid, rid);
        ensureUser("sec-" + ridB, ridB);
    }

    private void ensureUser(String loginId, Long restaurantId) {
        if (userRepository.findByLoginId(loginId).isEmpty()) {
            persistUser(loginId, restaurantId, UserRole.OWNER);
        }
    }

    private String userToken(Long restaurantId) {
        return jwtUtility.generateToken("sec-" + restaurantId, restaurantId, "OWNER");
    }

    // ── 1. Token rotation through real filter chain ──────────────────────────────

    @Test
    void oldToken_rejectedAfterRecovery_newTokenAccepted() throws Exception {
        RestaurantTerminal t = mkTerminal(rid, "A", "dev-A");
        String ut = userToken(rid);
        String oldTT = jwtUtility.generateTerminalToken("A", rid, "OWNER", t.getId().toString(), "A", "dev-A", 1L);

        // Old token works before rotation
        mockMvc.perform(post("/sync/bills/push").header("Authorization", "Bearer " + ut)
                .header("X-Terminal-Token", oldTT).contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isOk());

        // Recovery bumps credVer to 2
        TenantContext.setCurrentTenant(rid); TenantContext.setCurrentUserId(1L);
        terminalManagementService.recoverTerminal(t.getId(), rid, "dev-B", "OWNER");
        TenantContext.clear();

        // Old token rejected
        mockMvc.perform(post("/sync/bills/push").header("Authorization", "Bearer " + ut)
                .header("X-Terminal-Token", oldTT).contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isUnauthorized());

        // New token accepted
        RestaurantTerminal recovered = terminalRepository.findById(t.getId()).orElseThrow();
        String newTT = jwtUtility.generateTerminalToken("A", rid, "OWNER",
                recovered.getId().toString(), "A", "dev-B", recovered.getCredentialVersion());
        mockMvc.perform(post("/sync/bills/push").header("Authorization", "Bearer " + ut)
                .header("X-Terminal-Token", newTT).contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isOk());
    }

    @Test
    void deactivatedTerminal_tokenRejected() throws Exception {
        RestaurantTerminal t = mkTerminal(rid, "B", "dev-deact");
        String ut = userToken(rid);
        String tt = jwtUtility.generateTerminalToken("B", rid, "OWNER", t.getId().toString(), "B", "dev-deact", 1L);

        mockMvc.perform(post("/sync/bills/push").header("Authorization", "Bearer " + ut)
                .header("X-Terminal-Token", tt).contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isOk());

        TenantContext.setCurrentTenant(rid); TenantContext.setCurrentUserId(1L);
        terminalManagementService.deactivateTerminal(t.getId(), rid);
        TenantContext.clear();

        mockMvc.perform(post("/sync/bills/push").header("Authorization", "Bearer " + ut)
                .header("X-Terminal-Token", tt).contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isUnauthorized());
    }

    // ── 2. Legacy token revocation ───────────────────────────────────────────────

    @Test
    void futureVersionToken_rejected() throws Exception {
        RestaurantTerminal t = mkTerminal(rid, "K", "dev-future");
        // DB credential_version = 1. Token claims credVer = 3 (impossible/forged)
        String ut = userToken(rid);
        String futureToken = jwtUtility.generateTerminalToken("K", rid, "OWNER",
                t.getId().toString(), "K", "dev-future", 3L);

        mockMvc.perform(post("/sync/bills/push").header("Authorization", "Bearer " + ut)
                .header("X-Terminal-Token", futureToken).contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exactVersionMatch_accepted() throws Exception {
        RestaurantTerminal t = mkTerminal(rid, "L", "dev-exact");
        String ut = userToken(rid);
        // credential_version in DB = 1, token credVer = 1 → exact match
        String exactToken = jwtUtility.generateTerminalToken("L", rid, "OWNER",
                t.getId().toString(), "L", "dev-exact", 1L);

        mockMvc.perform(post("/sync/bills/push").header("Authorization", "Bearer " + ut)
                .header("X-Terminal-Token", exactToken).contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isOk());
    }

    @Test
    void legacyToken_acceptedBeforeRotation() throws Exception {
        RestaurantTerminal t = mkTerminal(rid, "C", "dev-leg");
        String ut = userToken(rid);
        String legacyTT = buildLegacyToken(t, rid); // no credVer claim

        // credential_version == 1 → legacy token accepted
        mockMvc.perform(post("/sync/bills/push").header("Authorization", "Bearer " + ut)
                .header("X-Terminal-Token", legacyTT).contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isOk());
    }

    @Test
    void legacyToken_rejectedAfterRecovery() throws Exception {
        RestaurantTerminal t = mkTerminal(rid, "D", "dev-leg2");
        String ut = userToken(rid);
        String legacyTT = buildLegacyToken(t, rid);

        // Recovery bumps credVer to 2
        TenantContext.setCurrentTenant(rid); TenantContext.setCurrentUserId(1L);
        terminalManagementService.recoverTerminal(t.getId(), rid, "dev-new", "OWNER");
        TenantContext.clear();

        // Legacy token (no credVer) must be rejected when DB credVer > 1
        mockMvc.perform(post("/sync/bills/push").header("Authorization", "Bearer " + ut)
                .header("X-Terminal-Token", legacyTT).contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyToken_rejectedAfterDeactivation() throws Exception {
        RestaurantTerminal t = mkTerminal(rid, "E", "dev-leg3");
        String ut = userToken(rid);
        String legacyTT = buildLegacyToken(t, rid);

        TenantContext.setCurrentTenant(rid); TenantContext.setCurrentUserId(1L);
        terminalManagementService.deactivateTerminal(t.getId(), rid);
        TenantContext.clear();

        mockMvc.perform(post("/sync/bills/push").header("Authorization", "Bearer " + ut)
                .header("X-Terminal-Token", legacyTT).contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isUnauthorized());
    }

    // ── 3. Cross-restaurant isolation ────────────────────────────────────────────

    @Test
    void crossRestaurant_requestStatus_404() throws Exception {
        mkTerminal(rid, "F", "dev-cross");
        TenantContext.setCurrentTenant(rid); TenantContext.setCurrentUserId(1L);
        var req = terminalManagementService.createRegistrationRequest(rid, 1L, "dev-x", "M", "NEW_DEVICE", null);
        TenantContext.clear();

        // Restaurant B cannot query Restaurant A's request
        mockMvc.perform(get("/sync/terminal/request-status/" + req.getId())
                .header("Authorization", "Bearer " + userToken(ridB)))
                .andExpect(status().isNotFound());

        // Restaurant A can
        mockMvc.perform(get("/sync/terminal/request-status/" + req.getId())
                .header("Authorization", "Bearer " + userToken(rid)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ── 4. Complete-activation security ──────────────────────────────────────────

    @Test
    void completeActivation_wrongDevice_forbidden() throws Exception {
        mkTerminal(rid, "G", "dev-exist");
        TenantContext.setCurrentTenant(rid); TenantContext.setCurrentUserId(1L);
        var req = terminalManagementService.createRegistrationRequest(rid, 1L, "dev-target", "M", "NEW_DEVICE", null);
        terminalManagementService.approveRequest(req.getId(), rid, 1L, "OWNER");
        TenantContext.clear();

        mockMvc.perform(post("/sync/terminal/complete-activation")
                .header("Authorization", "Bearer " + userToken(rid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":" + req.getId() + ",\"deviceId\":\"dev-attacker\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/sync/terminal/complete-activation")
                .header("Authorization", "Bearer " + userToken(rid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":" + req.getId() + ",\"deviceId\":\"dev-target\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.terminalToken").isNotEmpty());
    }

    @Test
    void completeActivation_idempotent() throws Exception {
        mkTerminal(rid, "H", "dev-idem-e");
        TenantContext.setCurrentTenant(rid); TenantContext.setCurrentUserId(1L);
        var req = terminalManagementService.createRegistrationRequest(rid, 1L, "dev-idem", "M", "NEW_DEVICE", null);
        terminalManagementService.approveRequest(req.getId(), rid, 1L, "OWNER");
        TenantContext.clear();

        String payload = "{\"requestId\":" + req.getId() + ",\"deviceId\":\"dev-idem\"}";
        mockMvc.perform(post("/sync/terminal/complete-activation")
                .header("Authorization", "Bearer " + userToken(rid))
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
        // Retry is idempotent
        mockMvc.perform(post("/sync/terminal/complete-activation")
                .header("Authorization", "Bearer " + userToken(rid))
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void completeActivation_rejectedRequest_denied() throws Exception {
        mkTerminal(rid, "I", "dev-rej-e");
        TenantContext.setCurrentTenant(rid); TenantContext.setCurrentUserId(1L);
        var req = terminalManagementService.createRegistrationRequest(rid, 1L, "dev-rej", "M", "NEW_DEVICE", null);
        terminalManagementService.rejectRequest(req.getId(), rid, 1L, "No");
        TenantContext.clear();

        mockMvc.perform(post("/sync/terminal/complete-activation")
                .header("Authorization", "Bearer " + userToken(rid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":" + req.getId() + ",\"deviceId\":\"dev-rej\"}"))
                .andExpect(status().isConflict());
    }

    // ── 5. Audit ─────────────────────────────────────────────────────────────────

    @Test
    void reclaim_crossRestaurant_rejected() throws Exception {
        // Create terminal in restaurant A
        mkTerminal(rid, "M", "dev-reclaim-M");

        // Restaurant B tries to reclaim it → 404 (terminal not found in their restaurant)
        mockMvc.perform(post("/sync/terminal/reclaim")
                .header("Authorization", "Bearer " + userToken(ridB))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"terminalSeries\":\"M\",\"deviceId\":\"dev-attacker\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reclaim_kbookAdmin_rejected() throws Exception {
        mkTerminal(rid, "N", "dev-reclaim-N");
        // Create a KBOOK_ADMIN user
        Long adminRid = ID_SEQ.getAndIncrement();
        String adminLoginId = "kbadmin-" + adminRid;
        if (userRepository.findByLoginId(adminLoginId).isEmpty()) {
            persistUser(adminLoginId, adminRid, UserRole.KBOOK_ADMIN);
        }
        String adminToken = jwtUtility.generateToken(adminLoginId, rid, "KBOOK_ADMIN");

        mockMvc.perform(post("/sync/terminal/reclaim")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"terminalSeries\":\"N\",\"deviceId\":\"dev-admin-device\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reclaim_rotatesCredentials() throws Exception {
        RestaurantTerminal t = mkTerminal(rid, "O", "dev-reclaim-old");
        String ut = userToken(rid);

        // Old token with credVer=1
        String oldTT = jwtUtility.generateTerminalToken("O", rid, "OWNER",
                t.getId().toString(), "O", "dev-reclaim-old", 1L);

        // Reclaim to new device
        mockMvc.perform(post("/sync/terminal/reclaim")
                .header("Authorization", "Bearer " + ut)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"terminalSeries\":\"O\",\"deviceId\":\"dev-reclaim-new\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminalSeries").value("O"))
                .andExpect(jsonPath("$.terminalToken").isNotEmpty());

        // Old token must be rejected (credVer rotated from 1 to 2)
        mockMvc.perform(post("/sync/bills/push")
                .header("Authorization", "Bearer " + ut)
                .header("X-Terminal-Token", oldTT)
                .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isUnauthorized());

        // Terminal ID and series remain unchanged
        RestaurantTerminal after = terminalRepository.findById(t.getId()).orElseThrow();
        assertThat(after.getTerminalSeries()).isEqualTo("O");
        assertThat(after.getId()).isEqualTo(t.getId());
        assertThat(after.getCredentialVersion()).isEqualTo(2L);
        assertThat(after.getDeviceId()).isEqualTo("dev-reclaim-new");
    }

    @Test
    void shopAdmin_cannotAccessBillSync() throws Exception {
        // Create a SHOP_ADMIN user for this restaurant
        Long shopAdminRid = rid;
        String shopAdminToken = jwtUtility.generateToken("shopadmin-" + shopAdminRid, shopAdminRid, "SHOP_ADMIN");
        // Ensure user exists with SHOP_ADMIN role
        if (userRepository.findByLoginId("shopadmin-" + shopAdminRid).isEmpty()) {
            persistUser("shopadmin-" + shopAdminRid, shopAdminRid, UserRole.SHOP_ADMIN);
        }

        // SHOP_ADMIN cannot access bill sync
        mockMvc.perform(post("/sync/bills/push")
                .header("Authorization", "Bearer " + shopAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
                .andExpect(status().isForbidden());

        // But SHOP_ADMIN CAN access terminal endpoints
        mockMvc.perform(get("/sync/terminal/list")
                .header("Authorization", "Bearer " + shopAdminToken))
                .andExpect(status().isOk());
    }

    @Test
    void recovery_producesAuditEvent() {
        RestaurantTerminal t = mkTerminal(rid, "J", "dev-aud");
        TenantContext.setCurrentTenant(rid); TenantContext.setCurrentUserId(1L); TenantContext.setCurrentRole("OWNER");
        terminalManagementService.recoverTerminal(t.getId(), rid, "dev-aud-new", "OWNER");
        TenantContext.clear();

        List<SecurityAuditEvent> events = auditRepository.findAll().stream()
                .filter(e -> "TERMINAL_RECOVERED".equals(e.getAction()) && rid.equals(e.getRestaurantId()))
                .toList();
        assertThat(events).isNotEmpty();
        assertThat(events.get(events.size() - 1).getTargetOwnerTerminal()).isEqualTo("dev-aud-new");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private RestaurantTerminal mkTerminal(Long restaurantId, String series, String deviceId) {
        return terminalRepository.findByRestaurantIdAndTerminalSeries(restaurantId, series)
                .orElseGet(() -> {
                    RestaurantTerminal t = new RestaurantTerminal();
                    t.setRestaurantId(restaurantId);
                    t.setTerminalSeries(series);
                    t.setTerminalName("Terminal " + series);
                    t.setDeviceId(deviceId);
                    t.setIsActive(true);
                    t.setStatus("ACTIVE");
                    t.setCredentialVersion(1L);
                    t.setCreatedAt(System.currentTimeMillis());
                    t.setUpdatedAt(System.currentTimeMillis());
                    return terminalRepository.save(t);
                });
    }

    /** Builds a terminal token WITHOUT the credVer claim (legacy pre-deployment token) */
    private String buildLegacyToken(RestaurantTerminal terminal, Long restaurantId) {
        java.util.Date now = new java.util.Date();
        return io.jsonwebtoken.Jwts.builder()
                .setId(java.util.UUID.randomUUID().toString())
                .setSubject(terminal.getTerminalSeries())
                .claim("restaurantId", restaurantId)
                .claim("role", "OWNER")
                .claim("terminalId", terminal.getId().toString())
                .claim("terminalSeries", terminal.getTerminalSeries())
                .claim("deviceId", terminal.getDeviceId())
                .claim("tokenType", "terminal")
                // deliberately omit "credVer"
                .setIssuedAt(now)
                .setExpiration(new java.util.Date(now.getTime() + 2592000000L))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "integration-test-secret-64-chars-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx".getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
