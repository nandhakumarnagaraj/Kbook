package com.khanabook.saas.controller;

import com.khanabook.saas.entity.MerchantAgreement;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.MerchantAgreementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Merchant agreement (KhanaBook &lt;-&gt; restaurant signed PDF) upload &amp; download.
 *
 * <p>All routes are authenticated. Owner routes live under {@code /business/**}
 * (OWNER, own tenant); the admin download lives under {@code /admin/**}
 * (KBOOK_ADMIN) — both already role-gated by SecurityConfig. Files are streamed
 * from a private path and are never publicly served.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class MerchantAgreementController {

    private final MerchantAgreementService service;

    // ---- Owner (tenant-scoped) --------------------------------------------

    @PostMapping(value = "/business/merchant-agreement", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "signerName", required = false) String signerName,
            @RequestParam(value = "agreementVersion", required = false) String agreementVersion) {
        Long restaurantId = TenantContext.getCurrentTenant();
        MerchantAgreement saved = service.upload(restaurantId, file, signerName, agreementVersion);
        Map<String, Object> body = new HashMap<>();
        body.put("uploaded", true);
        body.put("signedAt", saved.getSignedAt());
        body.put("originalFilename", saved.getOriginalFilename());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/business/merchant-agreement")
    public ResponseEntity<Map<String, Object>> status() {
        Long restaurantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(statusBody(service.get(restaurantId)));
    }

    @GetMapping("/business/merchant-agreement/download")
    public ResponseEntity<InputStreamResource> download() throws IOException {
        Long restaurantId = TenantContext.getCurrentTenant();
        return streamAgreement(service.get(restaurantId));
    }

    // ---- Platform admin ----------------------------------------------------

    @GetMapping("/admin/merchant-agreement/{restaurantId}")
    public ResponseEntity<Map<String, Object>> adminStatus(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(statusBody(service.get(restaurantId)));
    }

    @GetMapping("/admin/merchant-agreement/{restaurantId}/download")
    public ResponseEntity<InputStreamResource> adminDownload(@PathVariable Long restaurantId) throws IOException {
        return streamAgreement(service.get(restaurantId));
    }

    // ---- Helpers -----------------------------------------------------------

    private Map<String, Object> statusBody(Optional<MerchantAgreement> agreement) {
        Map<String, Object> body = new HashMap<>();
        if (agreement.isEmpty()) {
            body.put("hasAgreement", false);
            return body;
        }
        MerchantAgreement a = agreement.get();
        body.put("hasAgreement", true);
        body.put("signedAt", a.getSignedAt());
        body.put("signerName", a.getSignerName());
        body.put("agreementVersion", a.getAgreementVersion());
        body.put("originalFilename", a.getOriginalFilename());
        return body;
    }

    private ResponseEntity<InputStreamResource> streamAgreement(Optional<MerchantAgreement> agreementOpt)
            throws IOException {
        if (agreementOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        MerchantAgreement agreement = agreementOpt.get();
        InputStream stream = service.openStream(agreement);
        String filename = agreement.getOriginalFilename() != null
                ? agreement.getOriginalFilename() : "merchant-agreement.pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new InputStreamResource(stream));
    }
}
