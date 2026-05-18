package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.entity.EasebuzzSubMerchant;
import com.khanabook.saas.repository.EasebuzzSubMerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        double rate = ((Number) body.get("commissionRate")).doubleValue();
        sm.setCommissionRate(BigDecimal.valueOf(rate));
        sm.setUpdatedAt(System.currentTimeMillis());
        return ResponseEntity.ok(subMerchantRepository.save(sm));
    }

    @PutMapping("/default")
    public ResponseEntity<Map<String, Object>> setDefault(@RequestBody Map<String, Object> body) {
        double rate = ((Number) body.get("defaultRate")).doubleValue();
        CommissionConfigHolder.DEFAULT_RATE = rate;
        return ResponseEntity.ok(Map.of("defaultRate", rate));
    }

    private static final class CommissionConfigHolder {
        private static double DEFAULT_RATE = 0.0;

        private CommissionConfigHolder() {
        }
    }
}
