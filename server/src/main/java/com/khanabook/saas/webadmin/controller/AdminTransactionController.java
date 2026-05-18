package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.entity.EasebuzzWebhookEvent;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.EasebuzzWebhookEventRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.webadmin.dto.AdminTransactionResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/transactions")
@RequiredArgsConstructor
public class AdminTransactionController {

    private final EasebuzzWebhookEventRepository eventRepository;
    private final RestaurantProfileRepository profileRepository;

    @GetMapping
    public ResponseEntity<Page<AdminTransactionResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long restaurantId) {

        Specification<EasebuzzWebhookEvent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (restaurantId != null) {
                predicates.add(cb.equal(root.get("restaurantId"), restaurantId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt"));
        Page<EasebuzzWebhookEvent> eventPage = eventRepository.findAll(spec, pageRequest);

        Map<Long, String> shopNames = profileRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()
                .stream()
                .collect(Collectors.toMap(RestaurantProfile::getRestaurantId, RestaurantProfile::getShopName,
                        (a, b) -> a != null ? a : b));

        List<AdminTransactionResponse> responses = eventPage.getContent().stream()
                .map(e -> AdminTransactionResponse.builder()
                        .id(e.getId())
                        .restaurantId(e.getRestaurantId())
                        .shopName(shopNames.get(e.getRestaurantId()))
                        .txnId(e.getTxnId())
                        .easebuzzId(e.getEasebuzzId())
                        .status(e.getStatus())
                        .amount(e.getAmount())
                        .receivedAt(e.getReceivedAt())
                        .build())
                .toList();

        return ResponseEntity.ok(new PageImpl<>(responses, pageRequest, eventPage.getTotalElements()));
    }
}
