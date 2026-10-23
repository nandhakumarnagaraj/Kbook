package com.khanabook.saas.feature.payments.controller;

import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchant;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.core.exception.EntityNotFoundException;
import com.khanabook.saas.feature.payments.service.SubMerchantService;
import com.khanabook.saas.feature.payments.dto.PaymentConfigOpenApiSchemas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    @Operation(responses = @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PaymentConfigOpenApiSchemas.ConfigResponse.class))))
    public ResponseEntity<Map<String, Object>> getConfig() {
        Long restaurantId = TenantContext.getCurrentTenant();
        RestaurantProfile profile = profileRepo.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));
        EasebuzzSubMerchant sm = null;
        try {
            sm = subMerchantService.getByRestaurantId(restaurantId);
        } catch (EntityNotFoundException e) {
            // no sub-merchant configured yet
        }
        Map<String, Object> config = new HashMap<>();
        boolean subMerchantActive = sm != null && sm.getSubMerchantId() != null
                && !sm.getSubMerchantId().isBlank() && "ACTIVE".equals(sm.getStatus());
        boolean enabled = Boolean.TRUE.equals(profile.getEasebuzzEnabled());
        config.put("subMerchantActive", subMerchantActive);
        config.put("paymentLinkReady", enabled && subMerchantActive);
        config.put("easebuzzEnabled", enabled);
        config.put("subMerchantStatus", sm != null ? sm.getStatus() : "NOT_STARTED");
        config.put("subMerchantId", sm != null ? sm.getSubMerchantId() : null);
        config.put("kycStatus", sm != null ? sm.getKycStatus() : null);
        // The hosted KYC URL embeds an access key: anyone holding it signs in as this merchant's
        // KYC session. Only the owner may receive it — counter staff terminals poll this endpoint
        // for readiness and must never be handed a credential they have no use for.
        config.put("kycPortalUrl", ownerOnlyKycUrl(sm));
        config.put("kycSubmittedAt", sm != null ? sm.getKycSubmittedAt() : null);
        config.put("kycActivatedAt", sm != null ? sm.getKycActivatedAt() : null);
        return ResponseEntity.ok(config);
    }

    @PutMapping
    @Operation(requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = PaymentConfigOpenApiSchemas.UpdateRequest.class))), responses = @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PaymentConfigOpenApiSchemas.ConfigResponse.class))))
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> data) {
        if (!"OWNER".equals(TenantContext.getCurrentRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Restaurant owner access required");
        }
        Long restaurantId = TenantContext.getCurrentTenant();
        RestaurantProfile profile = profileRepo.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));
        if (data.containsKey("easebuzzEnabled")) {
            if (!(data.get("easebuzzEnabled") instanceof Boolean)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "easebuzzEnabled must be true or false");
            }
            boolean enabled = Boolean.TRUE.equals(data.get("easebuzzEnabled"));
            if (enabled) {
                EasebuzzSubMerchant sm;
                try {
                    sm = subMerchantService.getByRestaurantId(restaurantId);
                } catch (EntityNotFoundException e) {
                    return ResponseEntity.status(409).body(Map.of(
                            "status", "failure", "error", "Easebuzz sub-merchant must be active before enabling payments"));
                }
                if (sm.getSubMerchantId() == null || sm.getSubMerchantId().isBlank()
                        || !"ACTIVE".equals(sm.getStatus())) {
                    return ResponseEntity.status(409).body(Map.of(
                            "status", "failure", "error", "Easebuzz sub-merchant must be active before enabling payments"));
                }
            }
            profile.setEasebuzzEnabled(enabled);
            long now = System.currentTimeMillis();
            profile.setUpdatedAt(now);
            profile.setServerUpdatedAt(now);
            profile.markFieldsChanged(now, "easebuzzenabled");
            profile.setDeviceId("server");
        }
        profileRepo.save(profile);
        return getConfig();
    }

    private static boolean isOwner() {
        return "OWNER".equals(TenantContext.getCurrentRole());
    }

    /** The portal URL for an owner, {@code null} for every other role. */
    private static String ownerOnlyKycUrl(EasebuzzSubMerchant sm) {
        return (isOwner() && sm != null) ? sm.getKycPortalUrl() : null;
    }

    @GetMapping("/sub-merchant-status")
    @Operation(responses = @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PaymentConfigOpenApiSchemas.SubMerchantStatusResponse.class))))
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
            String portalUrl = ownerOnlyKycUrl(sm);
            result.put("kycUrl",            portalUrl != null ? portalUrl : "");
            result.put("kycExpiryDate",      sm.getKycExpiryDate() != null ? sm.getKycExpiryDate() : "");
            result.put("bankStatus",         sm.getBankStatus());
            result.put("activationDate",     sm.getKycActivatedAt() != null ? sm.getKycActivatedAt().toString() : null);
            result.put("businessProof1Type", sm.getBusinessProof1Type());
            result.put("businessProof2Type", sm.getBusinessProof2Type());
            result.put("businessAddress",    sm.getBusinessAddress());
            result.put("legalEntityName",    sm.getLegalEntityName());
            result.put("tradeName",          sm.getBusinessName());
            result.put("fssaiNumber",        sm.getFssaiNumber());
            // KYC documents are private PII: never return public /cdn/ URLs. Report
            // presence + the authenticated download path only. A document is present
            // if it has a private storage key (post-remediation) OR a legacy URL
            // (pre-reconciliation); either way the client fetches it via the
            // authenticated endpoint, never a direct link.
            putKycDoc(result, "idProof",        sm.getIdProofKey(),        sm.getIdProofUrl(),        "id_proof");
            putKycDoc(result, "bankProof",      sm.getBankProofKey(),      sm.getBankProofUrl(),      "bank_proof");
            putKycDoc(result, "businessProof1", sm.getBusinessProof1Key(), sm.getBusinessProof1Url(), "business_proof_1");
            putKycDoc(result, "businessProof2", sm.getBusinessProof2Key(), sm.getBusinessProof2Url(), "business_proof_2");
            return ResponseEntity.ok(result);
        } catch (EntityNotFoundException e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("status",             "NOT_REGISTERED");
            fallback.put("subMerchantId",      "");
            fallback.put("hasSubMerchant",     false);
            fallback.put("isActive",           false);
            fallback.put("kycStatus",          "");
            fallback.put("kycSubmissionDate",  null);
            fallback.put("kycUrl",            "");
            fallback.put("kycExpiryDate",      "");
            fallback.put("bankStatus",         null);
            fallback.put("activationDate",     null);
            fallback.put("businessProof1Type", null);
            fallback.put("businessProof2Type", null);
            fallback.put("businessAddress",    null);
            fallback.put("legalEntityName",    null);
            fallback.put("tradeName",          null);
            fallback.put("fssaiNumber",        null);
            putKycDoc(fallback, "idProof",        null, null, "id_proof");
            putKycDoc(fallback, "bankProof",      null, null, "bank_proof");
            putKycDoc(fallback, "businessProof1", null, null, "business_proof_1");
            putKycDoc(fallback, "businessProof2", null, null, "business_proof_2");
            return ResponseEntity.ok(fallback);
        }
    }

    /**
     * Emits KYC document presence without leaking a public URL:
     *   <prefix>Present  -> boolean
     *   <prefix>DownloadPath -> authenticated relative API path (or null)
     * Documents submitted to Easebuzz's servers carry the EASEBUZZ_HOSTED marker
     * (no local file), so they report present but expose no download path.
     */
    private void putKycDoc(Map<String, Object> result, String prefix, String key, String legacyUrl, String docType) {
        boolean hostedByEasebuzz = SubMerchantService.EASEBUZZ_HOSTED_MARKER.equals(key);
        boolean present = (key != null && !key.isBlank()) || (legacyUrl != null && !legacyUrl.isBlank());
        result.put(prefix + "Present", present);
        result.put(prefix + "DownloadPath",
                (present && !hostedByEasebuzz) ? "/business/kyc-document/" + docType + "/download" : null);
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
    @Operation(requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = PaymentConfigOpenApiSchemas.OnboardingRequest.class))), responses = @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PaymentConfigOpenApiSchemas.ActionResponse.class))))
    public ResponseEntity<Map<String, Object>> onboard(@RequestBody Map<String, Object> data) {
        Long restaurantId = TenantContext.getCurrentTenant();
        EasebuzzSubMerchant sm = subMerchantService.onboardForRestaurant(restaurantId, data);
        return ResponseEntity.ok(actionResult(sm));
    }

    /** Push corrected details to EaseBuzz after a KYC rejection. */
    @PostMapping("/resubmit")
    @Operation(requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = PaymentConfigOpenApiSchemas.OnboardingRequest.class))), responses = @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PaymentConfigOpenApiSchemas.ActionResponse.class))))
    public ResponseEntity<Map<String, Object>> resubmit(@RequestBody Map<String, Object> data) {
        Long restaurantId = TenantContext.getCurrentTenant();
        EasebuzzSubMerchant sm = subMerchantService.resubmitForRestaurant(restaurantId, data);
        return ResponseEntity.ok(actionResult(sm));
    }

    /**
     * Generate (or refresh) the EaseBuzz hosted KYC portal URL for document upload.
     * <p>
     * Owner-only: the response carries a portal access key, so issuing one on behalf of another
     * role would hand that role the merchant's KYC session.
     */
    @PostMapping("/kyc-access-key")
    @Operation(responses = @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PaymentConfigOpenApiSchemas.KycAccessKeyResponse.class))))
    public ResponseEntity<Map<String, Object>> kycAccessKey() {
        if (!isOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Restaurant owner access required");
        }
        Long restaurantId = TenantContext.getCurrentTenant();
        Long id = subMerchantService.requireSubMerchantIdForRestaurant(restaurantId);
        return ResponseEntity.ok(subMerchantService.generateKycAccessKey(id));
    }

    /**
     * Record that the merchant has a KYC document and return the portal link to upload it.
     * <p>
     * The app must use this instead of {@code POST /restaurants/kyc-document}: Easebuzz accepts
     * no documents through its API, so uploading the file only spent the merchant's bandwidth.
     */
    @PostMapping("/kyc-portal-link")
    @Operation(requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = PaymentConfigOpenApiSchemas.KycPortalLinkRequest.class))), responses = @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PaymentConfigOpenApiSchemas.KycPortalLinkResponse.class))))
    public ResponseEntity<Map<String, Object>> kycPortalLink(@RequestBody Map<String, Object> body) {
        // The response carries the same portal access key, so this inherits the owner-only rule
        // from /kyc-access-key rather than becoming the way around it.
        if (!isOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Restaurant owner access required");
        }
        Long restaurantId = TenantContext.getCurrentTenant();
        Object docType = body.get("docType");
        if (docType == null || docType.toString().isBlank()) {
            throw new IllegalArgumentException("docType is required");
        }
        return ResponseEntity.ok(subMerchantService.requestKycPortalLink(restaurantId, docType.toString()));
    }

    private Map<String, Object> actionResult(EasebuzzSubMerchant sm) {
        Map<String, Object> out = new HashMap<>();
        boolean ok = !"FAILED".equals(sm.getStatus());
        out.put("status", ok ? "success" : "failure");
        out.put("subMerchantId", sm.getSubMerchantId() != null ? sm.getSubMerchantId() : "");
        out.put("subMerchantStatus", sm.getStatus());
        out.put("kycStatus", sm.getKycStatus() != null ? sm.getKycStatus() : "");
        out.put("kycExpiryDate", sm.getKycExpiryDate() != null ? sm.getKycExpiryDate() : "");
        out.put("bankStatus", sm.getBankStatus());
        // Same rule as GET /restaurants/payment-config/easebuzz: the portal URL embeds an access
        // key that signs in as this merchant's KYC session, so only an owner receives it.
        String portalUrl = ownerOnlyKycUrl(sm);
        out.put("kycUrl", portalUrl != null ? portalUrl : "");
        out.put("message", Objects.toString(sm.getEasebuzzResponse(), ""));
        return out;
    }
}
