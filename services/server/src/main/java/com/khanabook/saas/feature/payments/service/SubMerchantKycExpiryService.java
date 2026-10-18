package com.khanabook.saas.feature.payments.service;

import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchant;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchantRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Easebuzz sends no webhook when a sub-merchant's KYC expires - the status simply stops being
 * valid and the merchant can no longer accept payments. The expiry date arrives once, on
 * MERCHANT_KYC_APPROVAL, so it is stored on our row and re-checked here.
 *
 * Moving the row off ACTIVE is what actually stops new payment links: payment creation
 * refuses any sub-merchant that is not ACTIVE (`SUBMERCHANT_NOT_ACTIVE`).
 *
 * The payload states no timezone. Easebuzz is an Indian gateway and the value is reported in
 * IST, so that is the assumption made here - deliberately in one place, rather than baked
 * into storage. A row whose date cannot be parsed is left ACTIVE and logged: silently
 * cutting off a merchant's payments on a parsing bug would be worse than a stale expiry.
 */
@Service
@RequiredArgsConstructor
public class SubMerchantKycExpiryService {

    private static final Logger log = LoggerFactory.getLogger(SubMerchantKycExpiryService.class);

    private static final DateTimeFormatter EASEBUZZ_EXPIRY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId EASEBUZZ_ZONE = ZoneId.of("Asia/Kolkata");

    private final EasebuzzSubMerchantRepository subMerchantRepo;

    @Scheduled(cron = "${easebuzz.kyc-expiry-check.cron:0 30 6 * * *}", zone = "Asia/Kolkata")
    public void flagExpiredKyc() {
        List<EasebuzzSubMerchant> active = subMerchantRepo.findByStatus("ACTIVE");
        if (active.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        int expired = 0;
        int unparseable = 0;

        for (EasebuzzSubMerchant sm : active) {
            String raw = sm.getKycExpiryDate();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                Instant expiresAt = LocalDateTime.parse(raw.trim(), EASEBUZZ_EXPIRY)
                        .atZone(EASEBUZZ_ZONE)
                        .toInstant();
                if (expiresAt.isAfter(now)) {
                    continue;
                }
                sm.setStatus("KYC_EXPIRED");
                sm.setKycStatus("False");
                sm.setUpdatedAt(now.toEpochMilli());
                subMerchantRepo.save(sm);
                expired++;
                log.warn("Sub-merchant {} KYC expired at {} - moved to KYC_EXPIRED, payments blocked",
                        sm.getSubMerchantId(), raw);
            } catch (DateTimeParseException e) {
                unparseable++;
                log.warn("Unparseable kyc_expiry_date '{}' for sub-merchant {} - left ACTIVE",
                        raw, sm.getSubMerchantId());
            } catch (Exception e) {
                // One bad row must not stop the batch: the rest of the estate still needs checking.
                log.error("Failed to apply KYC expiry for sub-merchant {}", sm.getSubMerchantId(), e);
            }
        }

        log.info("KYC expiry check finished: {} checked, {} expired, {} unparseable",
                active.size(), expired, unparseable);
    }
}
