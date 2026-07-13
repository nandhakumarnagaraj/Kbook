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
            existing.setUpdatedAt(System.currentTimeMillis());
            terminalRepository.save(existing);
            return ResponseEntity.ok(toResponse(existing));
        }

        Set<String> assignedSeries = new HashSet<>();
        for (RestaurantTerminal terminal : terminalRepository.findByRestaurantIdOrderByIdAsc(restaurantId)) {
            if (terminal.getTerminalSeries() != null) {
                assignedSeries.add(normalizeSeries(terminal.getTerminalSeries()));
            }
        }

        int seriesNumber = 1;
        while (assignedSeries.contains(seriesForNumber(seriesNumber))) {
            seriesNumber++;
        }
        String terminalSeries = seriesForNumber(seriesNumber);

        long now = System.currentTimeMillis();
        RestaurantTerminal terminal = new RestaurantTerminal();
        terminal.setRestaurantId(restaurantId);
        terminal.setTerminalSeries(terminalSeries);
        terminal.setTerminalName("Terminal " + terminalSeries);
        terminal.setDeviceId(deviceId);
        terminal.setIsActive(true);
        terminal.setCreatedAt(now);
        terminal.setUpdatedAt(now);
        RestaurantTerminal saved = terminalRepository.save(terminal);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(saved));
    }

    public record TerminalActivationRequest(String deviceId) {}

    public record TerminalActivationResponse(
            String terminalId,
            String terminalName,
            String terminalSeries,
            Boolean isActive,
            Long registeredAt,
            Long lastVerifiedAt) {}

    private TerminalActivationResponse toResponse(RestaurantTerminal terminal) {
        return new TerminalActivationResponse(
                terminal.getId() != null ? terminal.getId().toString() : terminal.getTerminalSeries(),
                terminal.getTerminalName(),
                terminal.getTerminalSeries(),
                terminal.getIsActive() == null ? Boolean.TRUE : terminal.getIsActive(),
                terminal.getCreatedAt(),
                terminal.getUpdatedAt());
    }

    private String seriesForNumber(int number) {
        if (number >= 1 && number <= 26) {
            return String.valueOf((char) ('A' + number - 1));
        }
        return "T" + number;
    }

    private String normalizeSeries(String series) {
        String trimmed = series == null ? "" : series.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        char first = Character.toUpperCase(trimmed.charAt(0));
        if (first >= 'A' && first <= 'Z') {
            return String.valueOf(first);
        }
        return trimmed.toUpperCase();
    }
}
