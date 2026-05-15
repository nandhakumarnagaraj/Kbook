package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.webadmin.dto.AdminSettlementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/settlements")
@RequiredArgsConstructor
public class AdminSettlementController {

    private final BillRepository billRepository;
    private final RestaurantProfileRepository profileRepository;

    @GetMapping
    public ResponseEntity<List<AdminSettlementResponse>> list() {
        List<Object[]> rows = billRepository.findSettlementSummary();

        Map<Long, String> shopNames = profileRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()
                .stream()
                .collect(Collectors.toMap(RestaurantProfile::getRestaurantId, RestaurantProfile::getShopName,
                        (a, b) -> a != null ? a : b));

        List<AdminSettlementResponse> responses = rows.stream()
                .map(r -> AdminSettlementResponse.builder()
                        .restaurantId(((Number) r[0]).longValue())
                        .shopName(shopNames.get(((Number) r[0]).longValue()))
                        .totalSettled(r[1] != null ? ((BigDecimal) r[1]) : BigDecimal.ZERO)
                        .totalCommission(r[2] != null ? ((BigDecimal) r[2]) : BigDecimal.ZERO)
                        .orderCount(((Number) r[3]).longValue())
                        .lastSettledAt(r[4] != null ? ((Number) r[4]).longValue() : null)
                        .build())
                .toList();

        return ResponseEntity.ok(responses);
    }
}
