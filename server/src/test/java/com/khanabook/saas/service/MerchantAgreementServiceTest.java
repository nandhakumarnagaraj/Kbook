package com.khanabook.saas.service;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.MerchantAgreement;
import com.khanabook.saas.entity.UserRole;
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

        MerchantAgreement saved = service.upload(restaurantId, file, "Test Owner", "v1");
        assertNotNull(saved.getId());
        assertNotNull(saved.getSignedAt());
        assertEquals("agreement.pdf", saved.getOriginalFilename());
        assertTrue(saved.getStorageKey().startsWith(restaurantId + "/"));

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
    void secondUploadReplacesFirst() throws Exception {
        Long restaurantId = 850L + System.currentTimeMillis() % 100;
        persistUser("agreerepl" + System.currentTimeMillis() + "@kbook.com", restaurantId, UserRole.OWNER);

        service.upload(restaurantId,
                new MockMultipartFile("file", "v1.pdf", "application/pdf", "%PDF-1".getBytes(StandardCharsets.UTF_8)),
                "Owner", "v1");
        MerchantAgreement second = service.upload(restaurantId,
                new MockMultipartFile("file", "v2.pdf", "application/pdf", "%PDF-2".getBytes(StandardCharsets.UTF_8)),
                "Owner", "v2");

        // Still exactly one agreement row for the restaurant (unique constraint), now pointing at v2.
        assertEquals("v2.pdf", second.getOriginalFilename());
        assertEquals("v2", service.get(restaurantId).orElseThrow().getAgreementVersion());
    }
}
