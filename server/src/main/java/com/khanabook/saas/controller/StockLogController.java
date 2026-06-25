package com.khanabook.saas.controller;

import com.khanabook.saas.sync.dto.PushSyncResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/sync/stocklog")
public class StockLogController {

    @PostMapping("/push")
    public ResponseEntity<PushSyncResponse> push(@RequestBody List<?> payload) {
        return ResponseEntity.ok(new PushSyncResponse(List.of(), List.of()));
    }
}
