package com.khanabook.saas.controller;

import com.khanabook.saas.entity.EasebuzzSubMerchant;
import com.khanabook.saas.repository.EasebuzzSubMerchantRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.AssetStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Authenticated download of EaseBuzz KYC documents (id proof, bank proof, two
 * business proofs). These are PII stored on a private filesystem path — never
 * under the Apache-public {@code /cdn/} alias — and are streamed only to the
 * owning tenant (OWNER) or platform admins (KBOOK_ADMIN).
 *
 * <p>Owner routes live under {@code /business/**} (tenant-scoped via
 * {@link TenantContext}); admin routes under {@code /admin/**}. Both path
 * prefixes are already role-gated by SecurityConfig. The private storage key is
 * never exposed to clients.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class KycDocumentController {

    private final EasebuzzSubMerchantRepository subMerchantRepo;
    private final AssetStorageService assetStorageService;

    // ---- Owner (tenant-scoped) --------------------------------------------

    @GetMapping("/business/kyc-document/{docType}/download")
    public ResponseEntity<InputStreamResource> ownerDownload(@PathVariable String docType) throws IOException {
        Long restaurantId = TenantContext.getCurrentTenant();
        return stream(restaurantId, docType);
    }

    // ---- Platform admin ----------------------------------------------------

    @GetMapping("/admin/kyc-document/{restaurantId}/{docType}/download")
    public ResponseEntity<InputStreamResource> adminDownload(
            @PathVariable Long restaurantId, @PathVariable String docType) throws IOException {
        return stream(restaurantId, docType);
    }

    // ---- Helpers -----------------------------------------------------------

    private ResponseEntity<InputStreamResource> stream(Long restaurantId, String docType) throws IOException {
        Optional<EasebuzzSubMerchant> smOpt = subMerchantRepo.findByRestaurantId(restaurantId);
        if (smOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String key = keyFor(smOpt.get(), docType);
        if (key == null || key.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        InputStream in;
        try {
            in = assetStorageService.openPrivateKycStream(key);
        } catch (IOException e) {
            log.warn("KYC document not found on disk for restaurant {} docType {}", restaurantId, docType);
            return ResponseEntity.notFound().build();
        }

        boolean isPdf = key.toLowerCase().endsWith(".pdf");
        MediaType contentType = isPdf ? MediaType.APPLICATION_PDF : MediaType.APPLICATION_OCTET_STREAM;
        String downloadName = docType + (isPdf ? ".pdf" : "");
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new InputStreamResource(in));
    }

    private String keyFor(EasebuzzSubMerchant sm, String docType) {
        return switch (docType) {
            case "id_proof" -> sm.getIdProofKey();
            case "bank_proof" -> sm.getBankProofKey();
            case "business_proof_1" -> sm.getBusinessProof1Key();
            case "business_proof_2" -> sm.getBusinessProof2Key();
            default -> throw new IllegalArgumentException("Unknown KYC document type: " + docType);
        };
    }
}
