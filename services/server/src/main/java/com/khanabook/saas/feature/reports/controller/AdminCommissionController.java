package com.khanabook.saas.feature.reports.controller;

import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchant;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/commission")
@RequiredArgsConstructor
public class AdminCommissionController {

    private final EasebuzzSubMerchantRepository subMerchantRepository;

    @GetMapping
    public ResponseEntity<List<EasebuzzSubMerchant>> list() {
        return ResponseEntity.ok(subMerchantRepository.findAll());
    }

    @PutMapping("/{subMerchantId}")
    public ResponseEntity<EasebuzzSubMerchant> update(@PathVariable Long subMerchantId,
                                                       @RequestBody Map<String, Object> body) {
        EasebuzzSubMerchant sm = subMerchantRepository.findById(subMerchantId)
                .orElseThrow(() -> new RuntimeException("SubMerchant not found: " + subMerchantId));
        requireZero(body.get("commissionRate"));
        sm.setCommissionRate(BigDecimal.ZERO);
        sm.setUpdatedAt(System.currentTimeMillis());
        return ResponseEntity.ok(subMerchantRepository.save(sm));
    }

    @PutMapping("/default")
    public ResponseEntity<Map<String, Object>> setDefault(@RequestBody Map<String, Object> body) {
        requireZero(body.get("defaultRate"));
        return ResponseEntity.ok(Map.of("defaultRate", 0));
    }

    private void requireZero(Object value) {
        try {
            if (value == null || new BigDecimal(value.toString()).compareTo(BigDecimal.ZERO) != 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "KhanaBook does not charge transaction commission");
            }
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid commission rate", e);
        }
    }
}
