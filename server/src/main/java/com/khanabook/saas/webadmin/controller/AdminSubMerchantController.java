package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.entity.EasebuzzSubMerchant;
import com.khanabook.saas.service.SubMerchantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/sub-merchants")
@RequiredArgsConstructor
public class AdminSubMerchantController {

    private final SubMerchantService subMerchantService;

    @GetMapping
    public ResponseEntity<List<EasebuzzSubMerchant>> list() {
        return ResponseEntity.ok(subMerchantService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EasebuzzSubMerchant> get(@PathVariable Long id) {
        return ResponseEntity.ok(subMerchantService.getById(id));
    }

    @PostMapping
    public ResponseEntity<EasebuzzSubMerchant> create(@RequestBody Map<String, Object> data) {
        Object rid = data.get("restaurantId");
        if (rid == null) {
            throw new IllegalArgumentException("restaurantId is required");
        }
        Long restaurantId = Long.valueOf(rid.toString());
        return ResponseEntity.ok(subMerchantService.create(data, restaurantId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EasebuzzSubMerchant> update(@PathVariable Long id,
                                                        @RequestBody Map<String, String> data) {
        return ResponseEntity.ok(subMerchantService.update(id, data));
    }

    /** Assign Easebuzz sub_merchant_id after manual creation in Easebuzz Dashboard */
    @PostMapping("/{id}/assign-id")
    public ResponseEntity<EasebuzzSubMerchant> assignSubMerchantId(@PathVariable Long id,
                                                                    @RequestBody Map<String, String> data) {
        return ResponseEntity.ok(subMerchantService.assignSubMerchantId(id, data.get("subMerchantId")));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<EasebuzzSubMerchant> updateStatus(@PathVariable Long id,
                                                              @RequestBody Map<String, String> data) {
        return ResponseEntity.ok(subMerchantService.updateStatus(id, data.get("status")));
    }

    /** Submit sub-merchant to Easebuzz API for KYC onboarding */
    @PostMapping("/{id}/submit-to-easebuzz")
    public ResponseEntity<EasebuzzSubMerchant> submitToEasebuzz(@PathVariable Long id) {
        return ResponseEntity.ok(subMerchantService.submitToEasebuzz(id));
    }

    /** Push local sub-merchant updates to Easebuzz */
    @PostMapping("/{id}/update-on-easebuzz")
    public ResponseEntity<EasebuzzSubMerchant> updateOnEasebuzz(@PathVariable Long id) {
        return ResponseEntity.ok(subMerchantService.updateOnEasebuzz(id));
    }

    /** Generate KYC portal access key for sub-merchant document upload */
    @PostMapping("/{id}/kyc-access-key")
    public ResponseEntity<Map<String, Object>> generateKycAccessKey(@PathVariable Long id) {
        return ResponseEntity.ok(subMerchantService.generateKycAccessKey(id));
    }

    /** Create Easebuzz split label for sub-merchant settlement */
    @PostMapping("/{id}/split-label")
    public ResponseEntity<Map<String, Object>> createSplitLabel(@PathVariable Long id) {
        return ResponseEntity.ok(subMerchantService.createSplitLabel(id));
    }

    /** Retrieve post-transaction split status */
    @PostMapping("/{id}/split-retrieve")
    public ResponseEntity<Map<String, Object>> retrieveTransactionSplit(@PathVariable Long id,
                                                                         @RequestBody Map<String, String> data) {
        return ResponseEntity.ok(subMerchantService.retrieveTransactionSplit(data.get("merchantRequestId")));
    }

    @PostMapping("/{id}/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyOtp(@PathVariable Long id, @RequestBody Map<String, String> data) {
        return ResponseEntity.ok(subMerchantService.verifyOtp(id, data.get("otp")));
    }

    @PostMapping("/{id}/resend-otp")
    public ResponseEntity<Map<String, Object>> resendOtp(@PathVariable Long id) {
        return ResponseEntity.ok(subMerchantService.resendOtp(id));
    }

    @PostMapping("/settlements/on-demand")
    public ResponseEntity<Map<String, Object>> onDemandSettlement(@RequestBody Map<String, String> data) {
        return ResponseEntity.ok(subMerchantService.initiateOnDemandSettlement(data.get("amount")));
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/payout")
    public ResponseEntity<Map<String, Object>> initiatePayout(@RequestBody Map<String, Object> data) {
        String amount = data.get("amount").toString();
        Map<String, String> beneficiaryDetails = (Map<String, String>) data.get("beneficiaryDetails");
        return ResponseEntity.ok(subMerchantService.initiatePayout(amount, beneficiaryDetails));
    }

    @GetMapping("/settlements/retrieve")
    public ResponseEntity<Map<String, Object>> retrieveSettlements(@RequestParam String date) {
        return ResponseEntity.ok(subMerchantService.retrieveSettlements(date));
    }

    /** DEV ONLY: Delete a sub-merchant to allow fresh retry */
    @DeleteMapping("/{id}/dev-refresh")
    public ResponseEntity<Void> devRefresh(@PathVariable Long id) {
        subMerchantService.deleteSubMerchant(id);
        return ResponseEntity.ok().build();
    }
}
