package com.khanabook.saas.service;

import com.khanabook.saas.entity.MerchantAgreement;
import com.khanabook.saas.repository.MerchantAgreementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores and serves the KhanaBook &lt;-&gt; restaurant signed agreement (Sejda PDF).
 *
 * <p>Documents are written to a PRIVATE filesystem path (not under the public
 * {@code /cdn/**} handler) and are only reachable via authenticated,
 * tenant-scoped controller endpoints. The DB row holds a relative storage key,
 * never a public URL. PDF only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantAgreementService {

    private final MerchantAgreementRepository repository;

    @Value("${kbook.private-docs.base-path}")
    private String basePath;

    @Value("${kbook.private-docs.max-upload-bytes}")
    private long maxUploadBytes;

    @Transactional
    public MerchantAgreement upload(Long restaurantId, MultipartFile file, String signerName, String agreementVersion) {
        validate(file);

        Path tmp = null;
        try {
            tmp = saveToTmp(file);

            // Store under a per-restaurant subdir with a random filename so a leaked
            // path element cannot be used to enumerate other tenants' documents.
            String filename = "agreement_" + UUID.randomUUID() + ".pdf";
            String relativeKey = restaurantId + "/" + filename;
            Path target = resolveWithinBase(relativeKey);
            Files.createDirectories(target.getParent());
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            tmp = null;

            long now = System.currentTimeMillis();
            MerchantAgreement agreement = repository.findByRestaurantId(restaurantId)
                    .orElseGet(() -> {
                        MerchantAgreement a = new MerchantAgreement();
                        a.setRestaurantId(restaurantId);
                        a.setCreatedAt(now);
                        return a;
                    });

            // Remove any previous file for this restaurant to avoid orphaned PII.
            String previousKey = agreement.getStorageKey();

            agreement.setStorageKey(relativeKey);
            agreement.setOriginalFilename(file.getOriginalFilename());
            agreement.setContentType(file.getContentType());
            agreement.setSizeBytes(file.getSize());
            agreement.setSignedAt(now);
            agreement.setSignerName(signerName);
            agreement.setAgreementVersion(agreementVersion);
            agreement.setUpdatedAt(now);
            MerchantAgreement saved = repository.save(agreement);

            if (previousKey != null && !previousKey.equals(relativeKey)) {
                deleteQuietly(previousKey);
            }

            log.info("Stored merchant agreement for restaurant {} key={}", restaurantId, relativeKey);
            return saved;
        } catch (IOException e) {
            throw new RuntimeException("Merchant agreement upload failed", e);
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            }
        }
    }

    public Optional<MerchantAgreement> get(Long restaurantId) {
        return repository.findByRestaurantId(restaurantId);
    }

    /** Opens the stored PDF for streaming. Caller is responsible for closing the stream. */
    public InputStream openStream(MerchantAgreement agreement) throws IOException {
        Path path = resolveWithinBase(agreement.getStorageKey());
        if (!Files.exists(path)) {
            throw new IOException("Agreement file missing on disk: " + agreement.getStorageKey());
        }
        return Files.newInputStream(path);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > maxUploadBytes) {
            throw new IllegalArgumentException("File too large; max " + maxUploadBytes + " bytes");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            throw new IllegalArgumentException("Only PDF uploads are allowed for the merchant agreement");
        }
    }

    private Path saveToTmp(MultipartFile file) throws IOException {
        Path tmpDir = Paths.get(basePath, "tmp");
        Files.createDirectories(tmpDir);
        Path tmp = tmpDir.resolve(UUID.randomUUID() + ".pdf");
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        return tmp;
    }

    /**
     * Resolves a relative storage key against the private base path and guards
     * against path traversal (a key like {@code ../../etc/passwd} must not escape
     * the base directory).
     */
    private Path resolveWithinBase(String relativeKey) {
        Path base = Paths.get(basePath).toAbsolutePath().normalize();
        Path resolved = base.resolve(relativeKey).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return resolved;
    }

    private void deleteQuietly(String relativeKey) {
        try {
            Files.deleteIfExists(resolveWithinBase(relativeKey));
        } catch (Exception e) {
            log.warn("Failed to delete previous agreement file {}", relativeKey, e);
        }
    }
}
