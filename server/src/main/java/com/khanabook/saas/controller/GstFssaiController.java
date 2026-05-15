package com.khanabook.saas.controller;

import com.khanabook.saas.service.GstFssaiLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/business/lookup")
@RequiredArgsConstructor
public class GstFssaiController {

    private final GstFssaiLookupService lookupService;

    @GetMapping("/gst")
    public ResponseEntity<Map<String, Object>> lookupGst(@RequestParam String gstin) {
        return ResponseEntity.ok(lookupService.lookupGst(gstin));
    }

    @GetMapping("/fssai")
    public ResponseEntity<Map<String, Object>> lookupFssai(@RequestParam String fssaiNo) {
        return ResponseEntity.ok(lookupService.lookupFssai(fssaiNo));
    }

    @GetMapping("/both")
    public ResponseEntity<Map<String, Object>> lookupBoth(
            @RequestParam(required = false) String gstin,
            @RequestParam(required = false) String fssaiNo) {
        return ResponseEntity.ok(lookupService.lookupBoth(gstin, fssaiNo));
    }
}
