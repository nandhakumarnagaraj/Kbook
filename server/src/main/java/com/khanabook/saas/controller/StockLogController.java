package com.khanabook.saas.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/sync/stocklog")
public class StockLogController {

    @PostMapping("/push")
    public ResponseEntity<Map<String, Object>> push() {
        return ResponseEntity.ok(Map.of(
                "successfulLocalIds", java.util.Collections.emptyList(),
                "failedLocalIds", java.util.Collections.emptyList()
        ));
    }
}
