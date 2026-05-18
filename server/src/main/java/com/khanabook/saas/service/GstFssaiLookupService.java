package com.khanabook.saas.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class GstFssaiLookupService {

    private static final Logger log = LoggerFactory.getLogger(GstFssaiLookupService.class);
    private final RestTemplate restTemplate = new RestTemplate();

    @SuppressWarnings("unchecked")
    public Map<String, Object> lookupGst(String gstin) {
        Map<String, Object> result = new HashMap<>();
        if (gstin == null || gstin.length() != 15) {
            result.put("valid", false);
            result.put("error", "Invalid GSTIN format");
            return result;
        }
        try {
            String url = "https://api.mastergst.com/gstinfo/" + gstin;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null) {
                result.put("valid", true);
                result.put("businessName", response.getOrDefault("tradeNam", ""));
                result.put("address", response.getOrDefault("addr", ""));
                result.put("state", response.getOrDefault("state", ""));
                result.put("taxType", response.getOrDefault("taxType", "regular"));
            } else {
                result.put("valid", false);
                result.put("error", "No data found");
            }
        } catch (Exception e) {
            log.warn("GST lookup failed for {}: {}", gstin, e.getMessage());
            result.put("valid", false);
            result.put("error", "Lookup service unavailable");
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> lookupFssai(String fssaiNo) {
        Map<String, Object> result = new HashMap<>();
        if (fssaiNo == null || fssaiNo.length() != 14) {
            result.put("valid", false);
            result.put("error", "Invalid FSSAI format (must be 14 digits)");
            return result;
        }
        try {
            String url = "https://api.fssai.gov.in/firm/" + fssaiNo;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null) {
                result.put("valid", true);
                result.put("businessName", response.getOrDefault("businessName", ""));
                result.put("address", response.getOrDefault("businessAddress", ""));
                result.put("fssaiStatus", response.getOrDefault("status", ""));
            } else {
                result.put("valid", false);
                result.put("error", "No data found");
            }
        } catch (Exception e) {
            log.warn("FSSAI lookup failed for {}: {}", fssaiNo, e.getMessage());
            result.put("valid", false);
            result.put("error", "Lookup service unavailable");
        }
        return result;
    }

    public Map<String, Object> lookupBoth(String gstin, String fssaiNo) {
        Map<String, Object> result = new HashMap<>();
        result.put("gst", lookupGst(gstin));
        result.put("fssai", lookupFssai(fssaiNo));
        if (result.get("gst") instanceof Map g && Boolean.TRUE.equals(g.get("valid"))) {
            result.put("businessName", g.get("businessName"));
            result.put("address", g.get("address"));
        }
        return result;
    }
}
