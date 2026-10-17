package com.khanabook.saas.feature.compliance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class GstFssaiLookupService {

    private static final Logger log = LoggerFactory.getLogger(GstFssaiLookupService.class);
    private final RestTemplate restTemplate;

    @org.springframework.beans.factory.annotation.Value("${fssai.lookup.url:https://iadv.in/tracker/dist/lic-info.php?lic_num=}")
    private String fssaiLookupUrl;

    public GstFssaiLookupService() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(12000);
        this.restTemplate = new RestTemplate(factory);
    }

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
                result.put("expiryDate", response.getOrDefault("expiryDate", response.getOrDefault("validTo", "")));
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
        if (fssaiNo == null || fssaiNo.trim().length() != 14) {
            result.put("valid", false);
            result.put("error", "Invalid FSSAI format (must be 14 digits)");
            return result;
        }
        String cleanFssaiNo = fssaiNo.trim();
        try {
            String url = (fssaiLookupUrl != null && !fssaiLookupUrl.isBlank())
                    ? fssaiLookupUrl + cleanFssaiNo
                    : "https://iadv.in/tracker/dist/lic-info.php?lic_num=" + cleanFssaiNo;

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && response.containsKey("license")) {
                Map<String, Object> license = (Map<String, Object>) response.get("license");
                Map<String, Object> details = (Map<String, Object>) response.getOrDefault("details", new HashMap<String, Object>());

                result.put("valid", true);
                result.put("licenseNo", String.valueOf(license.getOrDefault("LicenseNo", cleanFssaiNo)).trim());
                result.put("expiryDate", String.valueOf(license.getOrDefault("expiryDate", "")).trim());
                if (license.get("fboId") != null) result.put("fboId", license.get("fboId"));
                if (license.get("refId") != null) result.put("refId", license.get("refId"));

                String companyName = String.valueOf(details.getOrDefault("companyName", "")).trim();
                result.put("businessName", companyName);
                result.put("legalEntityName", companyName);
                result.put("contactPerson", String.valueOf(details.getOrDefault("contactPerson", "")).trim());
                result.put("address", String.valueOf(details.getOrDefault("addressPremises", "")).trim());
                result.put("state", String.valueOf(details.getOrDefault("statePremises", "")).trim());
                result.put("pincode", String.valueOf(details.getOrDefault("pincodePremises", "")).trim());
                result.put("contactEmail", String.valueOf(details.getOrDefault("contactEmail", "")).trim());

                Object panNo = details.get("panNo");
                if (panNo != null && !String.valueOf(panNo).trim().isBlank()) {
                    result.put("pan", String.valueOf(panNo).trim().toUpperCase());
                }

                if (details.containsKey("licenseCategoryName")) {
                    result.put("fssaiStatus", String.valueOf(details.get("licenseCategoryName")).trim());
                }
            } else {
                String error = (response != null && response.get("error") != null)
                        ? String.valueOf(response.get("error"))
                        : "No data found";
                result.put("valid", false);
                result.put("error", error);
            }
        } catch (Exception e) {
            log.warn("FSSAI lookup failed for {}: {}", cleanFssaiNo, e.getMessage());
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
