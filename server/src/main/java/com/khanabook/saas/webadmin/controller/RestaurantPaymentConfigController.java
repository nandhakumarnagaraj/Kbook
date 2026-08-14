package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.entity.EasebuzzSubMerchant;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.SubMerchantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/restaurants/payment-config/easebuzz")
@RequiredArgsConstructor
public class RestaurantPaymentConfigController {

    private final RestaurantProfileRepository profileRepo;
    private final SubMerchantService subMerchantService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        Long restaurantId = TenantContext.getCurrentTenant();
        RestaurantProfile profile = profileRepo.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));
        EasebuzzSubMerchant sm = null;
        try {
            sm = subMerchantService.getByRestaurantId(restaurantId);
        } catch (Exception e) {
            // no sub-merchant configured yet
        }
        Map<String, Object> config = new HashMap<>();
        // Auto-enable if a sub-merchant ID is assigned, otherwise use the stored flag
        boolean hasSubMerchantId = sm != null && sm.getSubMerchantId() != null && !sm.getSubMerchantId().isBlank();
        if (hasSubMerchantId) {
            subMerchantService.ensureEasebuzzEnabled(restaurantId);
        }
        config.put("easebuzzEnabled", profile.getEasebuzzEnabled() != null && profile.getEasebuzzEnabled());
        config.put("subMerchantStatus", sm != null ? sm.getStatus() : "NOT_STARTED");
        config.put("subMerchantId", sm != null ? sm.getSubMerchantId() : null);
        config.put("kycStatus", sm != null ? sm.getKycStatus() : null);
        config.put("kycPortalUrl", sm != null ? sm.getKycPortalUrl() : null);
        config.put("kycSubmittedAt", sm != null ? sm.getKycSubmittedAt() : null);
        config.put("kycActivatedAt", sm != null ? sm.getKycActivatedAt() : null);
        return ResponseEntity.ok(config);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> data) {
        Long restaurantId = TenantContext.getCurrentTenant();
        RestaurantProfile profile = profileRepo.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));
        if (data.containsKey("easebuzzEnabled")) {
            profile.setEasebuzzEnabled((Boolean) data.get("easebuzzEnabled"));
        }
        profileRepo.save(profile);
        return getConfig();
    }

    @GetMapping("/sub-merchant-status")
    public ResponseEntity<Map<String, Object>> getSubMerchantStatus() {
        Long restaurantId = TenantContext.getCurrentTenant();
        try {
            EasebuzzSubMerchant sm = subMerchantService.getByRestaurantId(restaurantId);
            boolean isActive = "ACTIVE".equals(sm.getStatus());
            boolean hasId    = sm.getSubMerchantId() != null && !sm.getSubMerchantId().isBlank();
            Map<String, Object> result = new HashMap<>();
            result.put("status",             sm.getStatus() != null ? sm.getStatus() : "NOT_REGISTERED");
            result.put("subMerchantId",      sm.getSubMerchantId() != null ? sm.getSubMerchantId() : "");
            result.put("hasSubMerchant",     hasId);
            result.put("isActive",           isActive);
            result.put("kycStatus",          sm.getKycStatus() != null ? sm.getKycStatus() : "");
            result.put("kycSubmissionDate",  sm.getKycSubmittedAt() != null ? sm.getKycSubmittedAt().toString() : null);
            result.put("kycUrl",            sm.getKycPortalUrl() != null ? sm.getKycPortalUrl() : "");
            result.put("activationDate",     sm.getKycActivatedAt() != null ? sm.getKycActivatedAt().toString() : null);
            result.put("idProofUrl",         sm.getIdProofUrl());
            result.put("bankProofUrl",       sm.getBankProofUrl());
            result.put("businessProof1Url",  sm.getBusinessProof1Url());
            result.put("businessProof2Url",  sm.getBusinessProof2Url());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("status",             "NOT_REGISTERED");
            fallback.put("subMerchantId",      "");
            fallback.put("hasSubMerchant",     false);
            fallback.put("isActive",           false);
            fallback.put("kycStatus",          "");
            fallback.put("kycSubmissionDate",  null);
            fallback.put("kycUrl",            "");
            fallback.put("activationDate",     null);
            fallback.put("idProofUrl",         null);
            fallback.put("bankProofUrl",       null);
            fallback.put("businessProof1Url",  null);
            fallback.put("businessProof2Url",  null);
            return ResponseEntity.ok(fallback);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Owner-driven onboarding & KYC (POS app). All actions are scoped to the
    // current tenant, so an owner can only ever act on their own sub-merchant.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Create (or re-onboard a DRAFT/FAILED) sub-merchant from the POS app and submit to EaseBuzz.
     * Body carries the onboarding details (business/legal name, type, PAN, GST, FSSAI, bank, etc.).
     */
    @PostMapping("/onboard")
    public ResponseEntity<Map<String, Object>> onboard(@RequestBody Map<String, Object> data) {
        Long restaurantId = TenantContext.getCurrentTenant();
        EasebuzzSubMerchant sm = subMerchantService.onboardForRestaurant(restaurantId, data);
        return ResponseEntity.ok(actionResult(sm));
    }

    /** Push corrected details to EaseBuzz after a KYC rejection. */
    @PostMapping("/resubmit")
    public ResponseEntity<Map<String, Object>> resubmit(@RequestBody Map<String, Object> data) {
        Long restaurantId = TenantContext.getCurrentTenant();
        EasebuzzSubMerchant sm = subMerchantService.resubmitForRestaurant(restaurantId, data);
        return ResponseEntity.ok(actionResult(sm));
    }

    /** Generate the EaseBuzz hosted KYC portal URL for document upload. */
    @PostMapping("/kyc-access-key")
    public ResponseEntity<Map<String, Object>> kycAccessKey() {
        Long restaurantId = TenantContext.getCurrentTenant();
        Long id = subMerchantService.requireSubMerchantIdForRestaurant(restaurantId);
        return ResponseEntity.ok(subMerchantService.generateKycAccessKey(id));
    }

    /** Verify the onboarding OTP sent by EaseBuzz (LIVE only — not supported in sandbox). */
    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyOtp(@RequestBody Map<String, Object> body) {
        Long restaurantId = TenantContext.getCurrentTenant();
        Long id = subMerchantService.requireSubMerchantIdForRestaurant(restaurantId);
        String otp = body.get("otp") != null ? body.get("otp").toString() : "";
        return ResponseEntity.ok(subMerchantService.verifyOtp(id, otp));
    }

    /** Resend the onboarding OTP (LIVE only — not supported in sandbox). */
    @PostMapping("/resend-otp")
    public ResponseEntity<Map<String, Object>> resendOtp() {
        Long restaurantId = TenantContext.getCurrentTenant();
        Long id = subMerchantService.requireSubMerchantIdForRestaurant(restaurantId);
        return ResponseEntity.ok(subMerchantService.resendOtp(id));
    }

    private Map<String, Object> actionResult(EasebuzzSubMerchant sm) {
        Map<String, Object> out = new HashMap<>();
        boolean ok = !"FAILED".equals(sm.getStatus());
        out.put("status", ok ? "success" : "failure");
        out.put("subMerchantId", sm.getSubMerchantId() != null ? sm.getSubMerchantId() : "");
        out.put("subMerchantStatus", sm.getStatus());
        out.put("kycStatus", sm.getKycStatus() != null ? sm.getKycStatus() : "");
        out.put("message", Objects.toString(sm.getEasebuzzResponse(), ""));
        return out;
    }
}
