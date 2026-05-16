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
        Long restaurantId = Long.valueOf(data.get("restaurantId").toString());
        return ResponseEntity.ok(subMerchantService.create(data, restaurantId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EasebuzzSubMerchant> update(@PathVariable Long id,
                                                        @RequestBody Map<String, String> data) {
        EasebuzzSubMerchant sm = subMerchantService.getById(id);
        if (data.containsKey("businessName")) sm.setBusinessName(data.get("businessName"));
        if (data.containsKey("businessType")) sm.setBusinessType(data.get("businessType"));
        if (data.containsKey("pan")) sm.setPan(data.get("pan"));
        if (data.containsKey("gst")) sm.setGst(data.get("gst"));
        if (data.containsKey("bankAccountNo")) sm.setBankAccountNo(data.get("bankAccountNo"));
        if (data.containsKey("ifsc")) sm.setIfsc(data.get("ifsc"));
        if (data.containsKey("bankName")) sm.setBankName(data.get("bankName"));
        if (data.containsKey("branchName")) sm.setBranchName(data.get("branchName"));
        if (data.containsKey("beneficiaryName")) sm.setBeneficiaryName(data.get("beneficiaryName"));
        if (data.containsKey("businessAddress")) sm.setBusinessAddress(data.get("businessAddress"));
        if (data.containsKey("contactEmail")) sm.setContactEmail(data.get("contactEmail"));
        if (data.containsKey("contactPhone")) sm.setContactPhone(data.get("contactPhone"));
        if (data.containsKey("commissionRate")) sm.setCommissionRate(new java.math.BigDecimal(data.get("commissionRate")));
        if (data.containsKey("upiDeductionLtLimit")) sm.setUpiDeductionLtLimit(new java.math.BigDecimal(data.get("upiDeductionLtLimit")));
        if (data.containsKey("dcDeductionGtTwoThousand")) sm.setDcDeductionGtTwoThousand(new java.math.BigDecimal(data.get("dcDeductionGtTwoThousand")));
        sm.setUpdatedAt(System.currentTimeMillis());
        subMerchantService.updateStatus(id, sm.getStatus());
        return ResponseEntity.ok(sm);
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
}
