package com.khanabook.saas.webadmin.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.khanabook.saas.entity.FeatureFlag;
import com.khanabook.saas.entity.FeatureFlagAudit;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.FeatureFlagAuditRepository;
import com.khanabook.saas.repository.FeatureFlagRepository;
import com.khanabook.saas.security.RequireRole;
import com.khanabook.saas.service.FeatureFlagService;
import com.khanabook.saas.webadmin.dto.FeatureFlagAdminResponse;

import lombok.RequiredArgsConstructor;

/**
 * Flag_Admin_Surface (Requirement 30.20-30.22, design section 1).
 *
 * Every mutation is restricted to KBOOK_ADMIN via the aspect (Requirement 5.5);
 * the endpoints sit under /admin/**, which SecurityConfig already restricts to
 * the KBOOK_ADMIN role, so the annotation is defence-in-depth. A flag that can
 * only be changed by direct SQL would not satisfy the audit/visibility contract
 * (Requirement 28.7 first rollback step), so this surface ships in Phase 2.
 */
@RestController
@RequestMapping("/admin/feature-flags")
@RequiredArgsConstructor
public class FeatureFlagAdminController {

    private final FeatureFlagService featureFlagService;
    private final FeatureFlagRepository flagRepository;
    private final FeatureFlagAuditRepository auditRepository;

    @GetMapping
    public ResponseEntity<List<FeatureFlagAdminResponse>> listFlags() {
        List<FeatureFlagAdminResponse> flags = flagRepository.findAll().stream()
                .map(flag -> new FeatureFlagAdminResponse(
                        flag.getFlagKey(),
                        flag.isKillSwitched(),
                        flag.isDefaultEnabled(),
                        flag.getDescription(),
                        flag.getUpdatedAt(),
                        // Effective global state: what a restaurant without an
                        // override would resolve to right now.
                        featureFlagService.isEnabled(flag.getFlagKey(), null)))
                .toList();
        return ResponseEntity.ok(flags);
    }

    @GetMapping("/{key}/audit")
    public ResponseEntity<List<FeatureFlagAudit>> getAudit(@PathVariable String key) {
        return ResponseEntity.ok(auditRepository.findByFlagKeyOrderByChangedAtDesc(key));
    }

    @PutMapping("/{key}/kill-switch")
    @RequireRole(UserRole.KBOOK_ADMIN)
    public ResponseEntity<Void> setKillSwitch(@PathVariable String key,
                                              @RequestBody Map<String, Boolean> body) {
        featureFlagService.setKillSwitch(key, body.getOrDefault("killSwitched", false));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{key}/default")
    @RequireRole(UserRole.KBOOK_ADMIN)
    public ResponseEntity<Void> setDefault(@PathVariable String key,
                                           @RequestBody Map<String, Boolean> body) {
        featureFlagService.setDefault(key, body.getOrDefault("defaultEnabled", false));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{key}/restaurants/{restaurantId}")
    @RequireRole(UserRole.KBOOK_ADMIN)
    public ResponseEntity<Void> setOverride(@PathVariable String key,
                                            @PathVariable Long restaurantId,
                                            @RequestBody Map<String, Boolean> body) {
        featureFlagService.setOverride(key, restaurantId, body.getOrDefault("enabled", false));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{key}/restaurants/{restaurantId}")
    @RequireRole(UserRole.KBOOK_ADMIN)
    public ResponseEntity<Void> clearOverride(@PathVariable String key,
                                              @PathVariable Long restaurantId) {
        featureFlagService.clearOverride(key, restaurantId);
        return ResponseEntity.ok().build();
    }
}
