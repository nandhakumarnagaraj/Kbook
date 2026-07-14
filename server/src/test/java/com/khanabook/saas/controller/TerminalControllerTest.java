package com.khanabook.saas.controller;

import com.khanabook.saas.entity.RestaurantTerminal;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.SecurityAuditService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerminalControllerTest {

    @Mock
    private RestaurantTerminalRepository terminalRepository;
    @Mock
    private BillRepository billRepository;
    @Mock
    private JwtUtility jwtUtility;
    @Mock
    private SecurityAuditService securityAuditService;

    private TerminalController controller;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(42L);
        TenantContext.setCurrentRole("OWNER");
        controller = new TerminalController(terminalRepository, billRepository, jwtUtility, securityAuditService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void activate_returnsExistingSeriesForTheSameDevice() {
        RestaurantTerminal existing = terminal("A3", "device-3");
        existing.setIsActive(true);
        when(terminalRepository.findByRestaurantIdAndDeviceId(42L, "device-3"))
                .thenReturn(Optional.of(existing));

        var response = controller.activate(new TerminalController.TerminalActivationRequest("device-3"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().terminalSeries()).isEqualTo("A3");
    }

    @Test
    void activate_assignsTheFirstUnusedSeries() {
        when(terminalRepository.findByRestaurantIdAndDeviceId(42L, "device-4"))
                .thenReturn(Optional.empty());
        when(terminalRepository.findByRestaurantIdOrderByIdAsc(42L))
                .thenReturn(List.of(terminal("A1", "device-1"), terminal("A3", "device-3")));
        when(terminalRepository.save(any(RestaurantTerminal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = controller.activate(new TerminalController.TerminalActivationRequest(" device-4 "));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().terminalSeries()).isEqualTo("B");
    }

    @Test
    void activate_issuesTerminalToken() {
        RestaurantTerminal existing = terminal("A1", "device-1");
        existing.setIsActive(true);
        when(terminalRepository.findByRestaurantIdAndDeviceId(42L, "device-1"))
                .thenReturn(Optional.of(existing));
        when(jwtUtility.generateTerminalToken(anyString(), any(), anyString(), anyString(), anyString(), any()))
                .thenReturn("terminal.jwt.token");

        var response = controller.activate(new TerminalController.TerminalActivationRequest("device-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().terminalToken()).isEqualTo("terminal.jwt.token");
    }

    @Test
    void activate_rejectsBlankDeviceId() {
        var response = controller.activate(new TerminalController.TerminalActivationRequest("  "));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void activate_rejectsDisabledTerminal() {
        RestaurantTerminal existing = terminal("A1", "device-1");
        existing.setIsActive(false);
        when(terminalRepository.findByRestaurantIdAndDeviceId(42L, "device-1"))
                .thenReturn(Optional.of(existing));

        var response = controller.activate(new TerminalController.TerminalActivationRequest("device-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static RestaurantTerminal terminal(String series, String deviceId) {
        RestaurantTerminal terminal = new RestaurantTerminal();
        terminal.setTerminalSeries(series);
        terminal.setDeviceId(deviceId);
        return terminal;
    }
}
