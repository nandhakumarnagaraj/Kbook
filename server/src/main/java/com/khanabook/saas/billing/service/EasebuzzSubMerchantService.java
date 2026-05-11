package com.khanabook.saas.billing.service;

import com.khanabook.saas.billing.dto.CreateSubMerchantRequest;
import com.khanabook.saas.billing.dto.KycAccessKeyResponse;
import com.khanabook.saas.billing.dto.SubMerchantListResponse;
import com.khanabook.saas.billing.dto.SubMerchantResponse;
import com.khanabook.saas.billing.entity.EasebuzzSubMerchant;
import com.khanabook.saas.billing.entity.EasebuzzSubMerchantWebhookEvent;
import com.khanabook.saas.billing.entity.SubMerchantStatus;
import com.khanabook.saas.billing.repository.EasebuzzSubMerchantRepository;
import com.khanabook.saas.billing.repository.EasebuzzSubMerchantWebhookEventRepository;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EasebuzzSubMerchantService {

    private final EasebuzzSubMerchantRepository repository;
    private final EasebuzzSubMerchantWebhookEventRepository webhookEventRepository;
    private final RestaurantProfileRepository restaurantProfileRepository;
    private final EasebuzzSubMerchantClient client;
    private final EasebuzzHashService hashService;
    private final CryptoService cryptoService;

    @Value("${easebuzz.master-merchant-key:}")
    private String masterMerchantKey;

    @Value("${easebuzz.master-salt:}")
    private String masterSalt;

    @Value("${easebuzz.master-environment:PROD}")
    private String masterEnvironment;

    @Transactional(readOnly = true)
    public SubMerchantListResponse list(Long restaurantId, SubMerchantStatus status) {
        List<EasebuzzSubMerchant> items;
        if (restaurantId != null) {
            items = repository.findByRestaurantId(restaurantId).stream().toList();
        } else if (status != null) {
            items = repository.findAllByStatusOrderByUpdatedAtDesc(status);
        } else {
            items = repository.findAllByOrderByUpdatedAtDesc();
        }
        return new SubMerchantListResponse(items.stream().map(this::toResponse).toList(), items.size());
    }

    @Transactional(readOnly = true)
    public SubMerchantResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public Optional<EasebuzzSubMerchant> findActiveByRestaurantId(Long restaurantId) {
        return repository.findByRestaurantId(restaurantId)
                .filter(existing -> existing.getStatus() == SubMerchantStatus.ACTIVE)
                .filter(existing -> existing.getSubMerchantId() != null && !existing.getSubMerchantId().isBlank());
    }

    @Transactional
    public SubMerchantResponse create(CreateSubMerchantRequest request) {
        if (repository.findByRestaurantId(request.restaurantId()).isPresent()) {
            throw new IllegalArgumentException("Sub-merchant already exists for this restaurant");
        }
        EasebuzzSubMerchant entity = new EasebuzzSubMerchant();
        long now = System.currentTimeMillis();
        entity.setRestaurantId(request.restaurantId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        copyRequest(entity, request);

        EasebuzzSubMerchantClient.SubMerchantGatewayResponse gatewayResponse = client.createSubMerchant(
                requireMasterMerchantKey(),
                hashService.buildSubMerchantCreateHash(
                        requireMasterMerchantKey(),
                        request.businessName().trim(),
                        request.contactEmail().trim(),
                        request.contactPhone().trim(),
                        requireMasterSalt()
                ),
                request
        );
        applyGatewayResponse(entity, gatewayResponse, now);
        repository.save(entity);
        return toResponse(entity);
    }

    @Transactional
    public SubMerchantResponse update(Long id, CreateSubMerchantRequest request) {
        EasebuzzSubMerchant entity = require(id);
        if (!entity.getRestaurantId().equals(request.restaurantId())) {
            throw new IllegalArgumentException("Restaurant id cannot be changed for an existing sub-merchant");
        }
        copyRequest(entity, request);
        long now = System.currentTimeMillis();
        entity.setUpdatedAt(now);

        if (entity.getSubMerchantId() != null && !entity.getSubMerchantId().isBlank()) {
            EasebuzzSubMerchantClient.SubMerchantGatewayResponse gatewayResponse = client.updateSubMerchant(
                    requireMasterMerchantKey(),
                    hashService.buildSubMerchantUpdateHash(
                            requireMasterMerchantKey(),
                            entity.getSubMerchantId(),
                            request.contactEmail().trim(),
                            request.contactPhone().trim(),
                            requireMasterSalt()
                    ),
                    entity.getSubMerchantId(),
                    request
            );
            applyGatewayResponse(entity, gatewayResponse, now);
        }

        repository.save(entity);
        return toResponse(entity);
    }

    @Transactional
    public KycAccessKeyResponse generateKycAccessKey(Long id) {
        EasebuzzSubMerchant entity = require(id);
        if (entity.getSubMerchantId() == null || entity.getSubMerchantId().isBlank()) {
            throw new IllegalArgumentException("Sub-merchant id is missing");
        }
        long now = System.currentTimeMillis();
        EasebuzzSubMerchantClient.SubMerchantGatewayResponse gatewayResponse = client.generateKycAccessKey(
                requireMasterMerchantKey(),
                hashService.buildSubMerchantKycAccessHash(
                        requireMasterMerchantKey(),
                        entity.getSubMerchantId(),
                        requireMasterSalt()
                ),
                entity.getSubMerchantId()
        );
        entity.setKycAccessKey(blankToNull(gatewayResponse.getKycAccessKey()));
        entity.setKycPortalUrl(blankToNull(gatewayResponse.getKycPortalUrl()));
        entity.setKycSubmittedAt(now);
        entity.setStatus(entity.getStatus() == SubMerchantStatus.ACTIVE ? SubMerchantStatus.ACTIVE : SubMerchantStatus.KYC_SUBMITTED);
        entity.setEasebuzzResponsePayload(gatewayResponse.getRawPayload());
        entity.setUpdatedAt(now);
        repository.save(entity);
        return KycAccessKeyResponse.builder()
                .subMerchantRecordId(entity.getId())
                .subMerchantId(entity.getSubMerchantId())
                .kycAccessKey(entity.getKycAccessKey())
                .kycPortalUrl(entity.getKycPortalUrl())
                .generatedAt(now)
                .build();
    }

    @Transactional
    public void processWebhook(Map<String, Object> payload) {
        String subMerchantId = firstNonBlank(
                value(payload, "sub_merchant_id"),
                value(payload, "merchant_id"));
        logWebhook(subMerchantId, payload);
        if (subMerchantId == null) {
            return;
        }
        EasebuzzSubMerchant entity = repository.findBySubMerchantId(subMerchantId)
                .orElseThrow(() -> new IllegalArgumentException("Sub-merchant not found"));
        String rawStatus = firstNonBlank(
                value(payload, "status"),
                value(payload, "sub_merchant_status"),
                value(payload, "kyc_status"));
        long now = System.currentTimeMillis();
        entity.setStatus(mapStatus(rawStatus, entity.getStatus()));
        if (entity.getStatus() == SubMerchantStatus.ACTIVE) {
            entity.setKycActivatedAt(now);
        }
        String kycAccessKey = value(payload, "access_key");
        if (kycAccessKey != null) {
            entity.setKycAccessKey(kycAccessKey);
        }
        String kycUrl = firstNonBlank(value(payload, "kyc_url"), value(payload, "portal_url"));
        if (kycUrl != null) {
            entity.setKycPortalUrl(kycUrl);
        }
        entity.setEasebuzzResponsePayload(payload.toString());
        entity.setUpdatedAt(now);
        repository.save(entity);
    }

    public String getMasterEnvironment() {
        return masterEnvironment == null || masterEnvironment.isBlank() ? "PROD" : masterEnvironment.trim().toUpperCase();
    }

    public String getMasterMerchantKey() {
        return requireMasterMerchantKey();
    }

    public String getConfiguredMasterMerchantKeyOrNull() {
        return masterMerchantKey == null || masterMerchantKey.isBlank() ? null : masterMerchantKey.trim();
    }

    public String getMasterSalt() {
        return requireMasterSalt();
    }

    private void copyRequest(EasebuzzSubMerchant entity, CreateSubMerchantRequest request) {
        entity.setBusinessName(request.businessName().trim());
        entity.setBusinessType(request.businessType().trim());
        entity.setPan(encrypt(request.pan().trim().toUpperCase()));
        entity.setGstin(encryptOptional(request.gstin()));
        entity.setBusinessAddress(encrypt(request.businessAddress().trim()));
        entity.setContactPerson(request.contactPerson().trim());
        entity.setContactEmail(encrypt(request.contactEmail().trim()));
        entity.setContactPhone(encrypt(request.contactPhone().trim()));
        entity.setBankAccountNumber(encrypt(request.bankAccountNumber().trim()));
        entity.setBankIfscCode(request.bankIfscCode().trim().toUpperCase());
        entity.setBankAccountHolderName(request.bankAccountHolderName().trim());
        entity.setBankName(request.bankName().trim());
    }

    private void applyGatewayResponse(
            EasebuzzSubMerchant entity,
            EasebuzzSubMerchantClient.SubMerchantGatewayResponse gatewayResponse,
            long now
    ) {
        if (gatewayResponse.getSubMerchantId() != null && !gatewayResponse.getSubMerchantId().isBlank()) {
            entity.setSubMerchantId(gatewayResponse.getSubMerchantId());
        }
        entity.setStatus(mapStatus(gatewayResponse.getGatewayStatus(), entity.getStatus()));
        entity.setEasebuzzResponsePayload(gatewayResponse.getRawPayload());
        entity.setUpdatedAt(now);
    }

    private SubMerchantResponse toResponse(EasebuzzSubMerchant entity) {
        RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(entity.getRestaurantId()).orElse(null);
        return SubMerchantResponse.builder()
                .id(entity.getId())
                .restaurantId(entity.getRestaurantId())
                .shopName(profile == null ? null : profile.getShopName())
                .subMerchantId(entity.getSubMerchantId())
                .status(entity.getStatus())
                .businessName(entity.getBusinessName())
                .businessType(entity.getBusinessType())
                .panMasked(mask(decryptMaybe(entity.getPan()), 4, 2))
                .gstinMasked(mask(decryptMaybe(entity.getGstin()), 4, 2))
                .businessAddress(decryptMaybe(entity.getBusinessAddress()))
                .contactPerson(entity.getContactPerson())
                .contactEmail(decryptMaybe(entity.getContactEmail()))
                .contactPhone(decryptMaybe(entity.getContactPhone()))
                .bankAccountNumberMasked(mask(decryptMaybe(entity.getBankAccountNumber()), 0, 4))
                .bankIfscCode(entity.getBankIfscCode())
                .bankAccountHolderName(entity.getBankAccountHolderName())
                .bankName(entity.getBankName())
                .kycPortalUrl(entity.getKycPortalUrl())
                .kycSubmittedAt(entity.getKycSubmittedAt())
                .kycActivatedAt(entity.getKycActivatedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private EasebuzzSubMerchant require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sub-merchant not found"));
    }

    private SubMerchantStatus mapStatus(String raw, SubMerchantStatus fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback == null ? SubMerchantStatus.PENDING : fallback;
        }
        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "active", "approved", "activated", "kyc_approved" -> SubMerchantStatus.ACTIVE;
            case "kyc_submitted", "submitted", "pending_kyc", "under_review" -> SubMerchantStatus.KYC_SUBMITTED;
            case "rejected", "declined", "kyc_rejected" -> SubMerchantStatus.REJECTED;
            case "suspended", "disabled", "blocked" -> SubMerchantStatus.SUSPENDED;
            default -> SubMerchantStatus.PENDING;
        };
    }

    private String requireMasterMerchantKey() {
        if (masterMerchantKey == null || masterMerchantKey.isBlank()) {
            throw new IllegalStateException("Easebuzz master merchant key is not configured");
        }
        return masterMerchantKey.trim();
    }

    private String requireMasterSalt() {
        if (masterSalt == null || masterSalt.isBlank()) {
            throw new IllegalStateException("Easebuzz master salt is not configured");
        }
        return masterSalt.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String value(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String mask(String value, int visiblePrefix, int visibleSuffix) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= Math.max(visiblePrefix + visibleSuffix, 4)) {
            return "*".repeat(trimmed.length());
        }
        return trimmed.substring(0, visiblePrefix)
                + "*".repeat(trimmed.length() - visiblePrefix - visibleSuffix)
                + trimmed.substring(trimmed.length() - visibleSuffix);
    }

    private void logWebhook(String subMerchantId, Map<String, Object> payload) {
        EasebuzzSubMerchantWebhookEvent event = new EasebuzzSubMerchantWebhookEvent();
        event.setSubMerchantId(subMerchantId);
        event.setEventType(firstNonBlank(value(payload, "event"), value(payload, "event_type"), "sub_merchant_webhook"));
        event.setRawStatus(firstNonBlank(value(payload, "status"), value(payload, "sub_merchant_status"), value(payload, "kyc_status")));
        event.setPayload(payload.toString());
        event.setProcessed(true);
        event.setReceivedAt(System.currentTimeMillis());
        webhookEventRepository.save(event);
    }

    private String encrypt(String value) {
        return cryptoService.encrypt(value);
    }

    private String encryptOptional(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : cryptoService.encrypt(normalized);
    }

    private String decryptMaybe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return cryptoService.decrypt(value);
        } catch (IllegalStateException ex) {
            return value;
        }
    }
}
