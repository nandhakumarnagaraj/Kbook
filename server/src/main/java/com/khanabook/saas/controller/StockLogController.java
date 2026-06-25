package com.khanabook.saas.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sync/stocklog")
public class StockLogController {

    @PostMapping("/push")
    public ResponseEntity<Map<String, Object>> push(@RequestBody List<Map<String, Object>> payload) {
        List<Long> successfulLocalIds = new ArrayList<>();
        for (Map<String, Object> entry : payload) {
            Object localId = entry.get("localId");
            if (localId instanceof Number n) {
                successfulLocalIds.add(n.longValue());
            }
        }
        return ResponseEntity.ok(Map.of(
                "successfulLocalIds", successfulLocalIds,
                "failedLocalIds", java.util.Collections.emptyList()
        ));
    }
}
