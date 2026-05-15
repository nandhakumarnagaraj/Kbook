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
    public ResponseEntity<EasebuzzSubMerchant> create(@RequestBody Map<String, String> data) {
        Long restaurantId = Long.valueOf(data.get("restaurantId"));
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
        if (data.containsKey("beneficiaryName")) sm.setBeneficiaryName(data.get("beneficiaryName"));
        if (data.containsKey("businessAddress")) sm.setBusinessAddress(data.get("businessAddress"));
        if (data.containsKey("contactEmail")) sm.setContactEmail(data.get("contactEmail"));
        if (data.containsKey("contactPhone")) sm.setContactPhone(data.get("contactPhone"));
        if (data.containsKey("commissionRate")) sm.setCommissionRate(new java.math.BigDecimal(data.get("commissionRate")));
        sm.setUpdatedAt(System.currentTimeMillis());
        subMerchantService.updateStatus(id, sm.getStatus());
        return ResponseEntity.ok(sm);
    }

    @PostMapping("/{id}/register")
    public ResponseEntity<EasebuzzSubMerchant> registerWithEasebuzz(@PathVariable Long id) {
        return ResponseEntity.ok(subMerchantService.registerWithEasebuzz(id));
    }

    @PostMapping("/{id}/generate-kyc")
    public ResponseEntity<EasebuzzSubMerchant> generateKyc(@PathVariable Long id) {
        return ResponseEntity.ok(subMerchantService.generateKyc(id));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<EasebuzzSubMerchant> updateStatus(@PathVariable Long id,
                                                             @RequestBody Map<String, String> data) {
        return ResponseEntity.ok(subMerchantService.updateStatus(id, data.get("status")));
    }

    @PostMapping("/{id}/kyc-status")
    public ResponseEntity<EasebuzzSubMerchant> refreshKycStatus(@PathVariable Long id) {
        return ResponseEntity.ok(subMerchantService.getById(id));
    }
}
