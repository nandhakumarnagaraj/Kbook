package com.khanabook.saas.controller;

import com.khanabook.saas.entity.DeviceRegistrationRequest;
import com.khanabook.saas.entity.RestaurantTerminal;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.DeviceRegistrationRequestRepository;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.SecurityAuditService;
import com.khanabook.saas.service.TerminalManagementService;
import com.khanabook.saas.utility.JwtUtility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerminalControllerTest {

    @Mock
    private RestaurantTerminalRepository terminalRepository;
    @Mock
    private BillRepository billRepository;
    @Mock
    private DeviceRegistrationRequestRepository requestRepository;
    @Mock
    private JwtUtility jwtUtility;
    @Mock
    private SecurityAuditService securityAuditService;
    @Mock
    private TerminalManagementService terminalManagementService;

    private TerminalController controller;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(42L);
        TenantContext.setCurrentRole("OWNER");
        TenantContext.setCurrentUserId(1L);
        controller = new TerminalController(terminalRepository, billRepository, requestRepository,
                jwtUtility, securityAuditService, terminalManagementService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void activate_returnsExistingTerminalForKnownDevice() {
        // Simulate device-1 already having a valid terminal token
        TenantContext.setCurrentTerminalDevice("device-1");

        RestaurantTerminal existing = terminal("A", "device-1");
        existing.setStatus("ACTIVE");
        existing.setCredentialVersion(1L);
        when(terminalRepository.findByRestaurantIdAndDeviceId(42L, "device-1"))
                .thenReturn(Optional.of(existing));
        when(jwtUtility.generateTerminalToken(anyString(), any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn("test.token");

        var response = controller.activate(new TerminalController.TerminalActivationRequest("device-1", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        TenantContext.setCurrentTerminalDevice(null);
    }

    @Test
    void activate_firstDeviceForNewRestaurant_autoCreatesTerminalA() {
        when(terminalRepository.findByRestaurantIdAndDeviceId(42L, "first-device"))
                .thenReturn(Optional.empty());
        when(terminalRepository.findByRestaurantIdOrderByIdAsc(42L))
                .thenReturn(List.of()); // No terminals exist
        when(terminalManagementService.lockRestaurantForTerminalOp(42L))
                .thenReturn(Optional.of(new com.khanabook.saas.entity.RestaurantProfile()));
        when(terminalRepository.save(any(RestaurantTerminal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtility.generateTerminalToken(anyString(), any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn("first.token");

        var response = controller.activate(new TerminalController.TerminalActivationRequest("first-device", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void activate_firstDeviceForNewRestaurant_onlyOwnerCanAutoCreate() {
        TenantContext.setCurrentRole("SHOP_ADMIN"); // Not OWNER

        when(terminalRepository.findByRestaurantIdAndDeviceId(42L, "admin-device"))
                .thenReturn(Optional.empty());
        when(terminalRepository.findByRestaurantIdOrderByIdAsc(42L))
                .thenReturn(List.of()); // No terminals exist

        // SHOP_ADMIN cannot auto-create the first terminal — should get PENDING
        DeviceRegistrationRequest pending = new DeviceRegistrationRequest();
        pending.setId(99L);
        pending.setStatus("PENDING");
        when(terminalManagementService.createOrReuseRegistrationRequest(
                eq(42L), eq(1L), eq("admin-device"), isNull(), eq("NEW_DEVICE"), isNull()))
                .thenReturn(pending);

        var response = controller.activate(new TerminalController.TerminalActivationRequest("admin-device", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void activate_unknownDeviceCannotHijackSingleTerminal() {
        // Restaurant has exactly one terminal bound to device-A.
        // An unknown device-B should NOT automatically take over Terminal A.
        RestaurantTerminal existingTerminal = terminal("A", "device-A");
        existingTerminal.setStatus("ACTIVE");
        existingTerminal.setId(1L);

        when(terminalRepository.findByRestaurantIdAndDeviceId(42L, "device-B"))
                .thenReturn(Optional.empty());
        when(terminalRepository.findByRestaurantIdOrderByIdAsc(42L))
                .thenReturn(List.of(existingTerminal));

        DeviceRegistrationRequest pending = new DeviceRegistrationRequest();
        pending.setId(77L);
        pending.setStatus("PENDING");
        when(terminalManagementService.createOrReuseRegistrationRequest(
                eq(42L), eq(1L), eq("device-B"), isNull(), eq("NEW_DEVICE"), isNull()))
                .thenReturn(pending);

        var response = controller.activate(new TerminalController.TerminalActivationRequest("device-B", null));

        // Must get PENDING_APPROVAL, NOT auto-rebind
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void activate_knownDeviceWithoutToken_requiresRecovery() {
        // Terminal A is bound to device-A. Caller presents device-A's deviceId
        // but has NO terminal token (TenantContext.getCurrentTerminalDevice() is null).
        RestaurantTerminal existingTerminal = terminal("A", "device-A");
        existingTerminal.setStatus("ACTIVE");
        existingTerminal.setId(5L);

        when(terminalRepository.findByRestaurantIdAndDeviceId(42L, "device-A"))
                .thenReturn(Optional.of(existingTerminal));

        DeviceRegistrationRequest recoveryReq = new DeviceRegistrationRequest();
        recoveryReq.setId(88L);
        recoveryReq.setStatus("PENDING");
        when(terminalManagementService.createOrReuseRegistrationRequest(
                eq(42L), eq(1L), eq("device-A"), isNull(), eq("RECOVERY"), eq(5L)))
                .thenReturn(recoveryReq);

        // No terminal token in context (null)
        var response = controller.activate(new TerminalController.TerminalActivationRequest("device-A", null));

        // Without proof of possession, known device gets RECOVERY request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void activate_rejectsDeactivatedTerminal() {
        // Simulate device having its terminal token
        TenantContext.setCurrentTerminalDevice("device-1");

        RestaurantTerminal existing = terminal("A1", "device-1");
        existing.setIsActive(false);
        existing.setStatus("INACTIVE");
        when(terminalRepository.findByRestaurantIdAndDeviceId(42L, "device-1"))
                .thenReturn(Optional.of(existing));

        var response = controller.activate(new TerminalController.TerminalActivationRequest("device-1", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        TenantContext.setCurrentTerminalDevice(null);
    }

    @Test
    void activate_rejectsBlankDeviceId() {
        var response = controller.activate(new TerminalController.TerminalActivationRequest("  ", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void activate_rejectionCooldown_returns429() {
        when(terminalRepository.findByRestaurantIdAndDeviceId(42L, "rejected-device"))
                .thenReturn(Optional.empty());
        when(terminalRepository.findByRestaurantIdOrderByIdAsc(42L))
                .thenReturn(List.of(terminal("A", "device-existing")));

        // Cooldown returns null
        when(terminalManagementService.createOrReuseRegistrationRequest(
                eq(42L), eq(1L), eq("rejected-device"), isNull(), eq("NEW_DEVICE"), isNull()))
                .thenReturn(null);

        var response = controller.activate(new TerminalController.TerminalActivationRequest("rejected-device", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private static RestaurantTerminal terminal(String series, String deviceId) {
        RestaurantTerminal terminal = new RestaurantTerminal();
        terminal.setTerminalSeries(series);
        terminal.setDeviceId(deviceId);
        terminal.setStatus("ACTIVE");
        terminal.setCredentialVersion(1L);
        terminal.setIsActive(true);
        return terminal;
    }
}
