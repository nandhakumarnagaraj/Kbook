package com.khanabook.saas.controller;

import com.khanabook.saas.entity.RestaurantTerminal;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/sync/terminal")
@RequiredArgsConstructor
public class TerminalController {

    private final RestaurantTerminalRepository terminalRepository;

    @PostMapping("/activate")
    @Transactional
    public ResponseEntity<TerminalActivationResponse> activate(
            @RequestBody TerminalActivationRequest request) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null || request == null || request.deviceId() == null
                || request.deviceId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String deviceId = request.deviceId().trim();
        RestaurantTerminal existing = terminalRepository
                .findByRestaurantIdAndDeviceId(restaurantId, deviceId)
                .orElse(null);
        if (existing != null) {
            return ResponseEntity.ok(new TerminalActivationResponse(existing.getTerminalSeries()));
        }

        Set<String> assignedSeries = new HashSet<>();
        for (RestaurantTerminal terminal : terminalRepository.findByRestaurantIdOrderByIdAsc(restaurantId)) {
            if (terminal.getTerminalSeries() != null) {
                assignedSeries.add(terminal.getTerminalSeries());
            }
        }

        int seriesNumber = 1;
        while (assignedSeries.contains("A" + seriesNumber)) {
            seriesNumber++;
        }

        long now = System.currentTimeMillis();
        RestaurantTerminal terminal = new RestaurantTerminal();
        terminal.setRestaurantId(restaurantId);
        terminal.setTerminalSeries("A" + seriesNumber);
        terminal.setDeviceId(deviceId);
        terminal.setCreatedAt(now);
        terminal.setUpdatedAt(now);
        terminalRepository.save(terminal);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new TerminalActivationResponse(terminal.getTerminalSeries()));
    }

    public record TerminalActivationRequest(String deviceId) {}

    public record TerminalActivationResponse(String terminalSeries) {}
}
