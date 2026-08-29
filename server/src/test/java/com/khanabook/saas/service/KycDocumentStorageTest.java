package com.khanabook.saas.service;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.EasebuzzSubMerchant;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.EasebuzzSubMerchantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies KYC documents are stored on the PRIVATE path (never /cdn/), keyed in
 * the DB, and streamable back; and that path traversal is rejected.
 */
class KycDocumentStorageTest extends BaseIntegrationTest {

    @Autowired private AssetStorageService assetStorageService;
    @Autowired private EasebuzzSubMerchantRepository subMerchantRepo;

    @Value("${kbook.cdn.base-path}") private String cdnBasePath;
    @Value("${kbook.private-docs.base-path}") private String privateBasePath;

    @DynamicPropertySource
    static void paths(DynamicPropertyRegistry registry) {
        registry.add("kbook.cdn.base-path", () -> System.getProperty("java.io.tmpdir") + "/kyc-test-cdn");
        registry.add("kbook.cdn.url-prefix", () -> "https://test.local/cdn/");
        registry.add("kbook.cdn.tmp-path", () -> System.getProperty("java.io.tmpdir") + "/kyc-test-cdn/tmp");
        registry.add("kbook.cdn.cwebp-bin", () -> "");
        registry.add("kbook.cdn.max-upload-bytes", () -> "10485760");
        registry.add("kbook.private-docs.base-path", () -> System.getProperty("java.io.tmpdir") + "/kyc-test-private");
        registry.add("kbook.private-docs.max-upload-bytes", () -> "10485760");
    }

    private EasebuzzSubMerchant seedSubMerchant(Long restaurantId) {
        persistUser("kyc" + System.currentTimeMillis() + "@kbook.com", restaurantId, UserRole.OWNER);
        EasebuzzSubMerchant sm = new EasebuzzSubMerchant();
        sm.setRestaurantId(restaurantId);
        sm.setBusinessName("KYC Test");
        sm.setStatus("DRAFT");
        sm.setCreatedAt(System.currentTimeMillis());
        sm.setUpdatedAt(System.currentTimeMillis());
        return subMerchantRepo.save(sm);
    }

    @Transactional
    @Test
    void kycUploadStoresPrivateKeyNotCdnUrl() throws Exception {
        Long restaurantId = 600L + System.currentTimeMillis() % 100;
        seedSubMerchant(restaurantId);

        byte[] pdf = "%PDF-1.4 business proof".getBytes(StandardCharsets.UTF_8);
        assetStorageService.uploadKycDocument(restaurantId, "business_proof_1",
                new MockMultipartFile("file", "proof.pdf", "application/pdf", pdf));

        EasebuzzSubMerchant sm = subMerchantRepo.findByRestaurantId(restaurantId).orElseThrow();
        // Stored as a private key under kyc/, not a /cdn/ URL.
        assertNotNull(sm.getBusinessProof1Key());
        assertTrue(sm.getBusinessProof1Key().startsWith("kyc/" + restaurantId + "/"));
        assertFalse(sm.getBusinessProof1Key().contains("/cdn/"));
        // Legacy public URL is cleared.
        assertNull(sm.getBusinessProof1Url());

        // Round-trips through the private stream.
        try (InputStream in = assetStorageService.openPrivateKycStream(sm.getBusinessProof1Key())) {
            assertArrayEquals(pdf, in.readAllBytes());
        }
    }

    @Transactional
    @Test
    void openPrivateKycStreamRejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> assetStorageService.openPrivateKycStream("../../etc/passwd"));
    }

    @Transactional
    @Test
    void logoUploadStillUsesPublicCdnUrl() {
        Long restaurantId = 650L + System.currentTimeMillis() % 100;
        persistUser("logo" + System.currentTimeMillis() + "@kbook.com", restaurantId, UserRole.OWNER);

        AssetStorageService.AssetUploadResult result = assetStorageService.uploadLogo(restaurantId,
                new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1, 2, 3, 4}));

        // Logos remain public: URL points at the /cdn/ path (unchanged behaviour).
        assertNotNull(result.url());
        assertTrue(result.url().contains("/cdn/"));
    }
}
