package com.khanabook.saas.service;

import com.khanabook.saas.feature.onboarding.service.MerchantAgreementService;
import com.khanabook.saas.feature.onboarding.service.MerchantAgreementTerms;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.feature.onboarding.entity.MerchantAgreement;
import com.khanabook.saas.feature.onboarding.data.MerchantAgreementRepository;
import com.khanabook.saas.feature.auth.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MerchantAgreementServiceTest extends BaseIntegrationTest {

    @Autowired private MerchantAgreementService service;
    @Autowired private MerchantAgreementRepository agreementRepository;

    @DynamicPropertySource
    static void privateDocsPath(DynamicPropertyRegistry registry) {
        registry.add("kbook.private-docs.base-path",
                () -> System.getProperty("java.io.tmpdir") + "/kbook-private-test");
        registry.add("kbook.private-docs.max-upload-bytes", () -> "10485760");
    }

    @Transactional
    @Test
    void uploadThenRetrieveRoundTrips() throws Exception {
        Long restaurantId = 700L + System.currentTimeMillis() % 100;
        persistUser("agreeadmin" + System.currentTimeMillis() + "@kbook.com", restaurantId, UserRole.OWNER);

        byte[] pdfBytes = "%PDF-1.4 fake signed agreement".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "agreement.pdf", "application/pdf", pdfBytes);

        MerchantAgreement saved = service.upload(restaurantId, file, "Test Owner", MerchantAgreementTerms.VERSION);
        assertNotNull(saved.getId());
        assertNotNull(saved.getSignedAt());
        assertEquals("agreement.pdf", saved.getOriginalFilename());
        assertTrue(saved.getStorageKey().startsWith(restaurantId + "/"));
        assertEquals(MerchantAgreementTerms.SHA256, saved.getTermsSha256());
        assertEquals(MerchantAgreementTerms.TEXT, saved.getTermsText());
        assertTrue(service.hasCurrentSignedAgreement(restaurantId));

        MerchantAgreement fetched = service.get(restaurantId).orElseThrow();
        try (InputStream in = service.openStream(fetched)) {
            byte[] read = in.readAllBytes();
            assertArrayEquals(pdfBytes, read);
        }
    }

    @Transactional
    @Test
    void rejectsNonPdfUpload() {
        Long restaurantId = 800L + System.currentTimeMillis() % 100;
        persistUser("agreeimg" + System.currentTimeMillis() + "@kbook.com", restaurantId, UserRole.OWNER);

        MockMultipartFile image = new MockMultipartFile(
                "file", "proof.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThrows(IllegalArgumentException.class,
                () -> service.upload(restaurantId, image, null, null));
    }

    @Transactional
    @Test
    void secondUploadCreatesImmutableRevision() throws Exception {
        Long restaurantId = 850L + System.currentTimeMillis() % 100;
        persistUser("agreerepl" + System.currentTimeMillis() + "@kbook.com", restaurantId, UserRole.OWNER);

        service.upload(restaurantId,
                new MockMultipartFile("file", "v1.pdf", "application/pdf", "%PDF-1".getBytes(StandardCharsets.UTF_8)),
                "Owner", MerchantAgreementTerms.VERSION);
        MerchantAgreement second = service.upload(restaurantId,
                new MockMultipartFile("file", "v2.pdf", "application/pdf", "%PDF-2".getBytes(StandardCharsets.UTF_8)),
                "Owner", MerchantAgreementTerms.VERSION);

        // Revisions are retained for audit; the status endpoint returns the newest revision.
        assertEquals("v2.pdf", second.getOriginalFilename());
        assertEquals(MerchantAgreementTerms.VERSION, service.get(restaurantId).orElseThrow().getAgreementVersion());
        assertEquals(2, agreementRepository.findAll().stream()
                .filter(a -> restaurantId.equals(a.getRestaurantId())).count());
        assertNotNull(second.getDocumentSha256());
    }

    @Transactional
    @Test
    void rejectsStaleTermsHash() {
        Long restaurantId = 950L + System.currentTimeMillis() % 100;
        persistUser("agreestale" + System.currentTimeMillis() + "@kbook.com", restaurantId, UserRole.OWNER);
        MockMultipartFile file = new MockMultipartFile("file", "signed.pdf", "application/pdf",
                "%PDF-1.4 signed".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(restaurantId, file, "Owner", MerchantAgreementTerms.VERSION,
                        1L, "DRAWN_SIGNATURE_UPLOAD", "outdated"));
        assertFalse(service.hasCurrentSignedAgreement(restaurantId));
    }
}
