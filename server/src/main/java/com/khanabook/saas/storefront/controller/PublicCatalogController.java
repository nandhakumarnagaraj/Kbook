package com.khanabook.saas.storefront.controller;

import com.khanabook.saas.storefront.dto.StorefrontCatalogResponse;
import com.khanabook.saas.storefront.service.StorefrontService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public/stores")
@RequiredArgsConstructor
public class PublicCatalogController {

    private final StorefrontService storefrontService;

    @GetMapping("/{restaurantId}/catalog")
    public ResponseEntity<StorefrontCatalogResponse> getCatalog(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(storefrontService.getCatalog(restaurantId));
    }
}
