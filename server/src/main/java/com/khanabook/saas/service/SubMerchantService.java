package com.khanabook.saas.service;

import com.khanabook.saas.entity.EasebuzzSubMerchant;
import com.khanabook.saas.entity.EasebuzzSubMerchantWebhookEvent;
import com.khanabook.saas.repository.EasebuzzSubMerchantRepository;
import com.khanabook.saas.repository.EasebuzzSubMerchantWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SubMerchantService {

    private static final Logger log = LoggerFactory.getLogger(SubMerchantService.class);
    private final EasebuzzApiClient easebuzzApi;
    private final EasebuzzSubMerchantRepository subMerchantRepo;
    private final EasebuzzSubMerchantWebhookEventRepository webhookEventRepo;

    public List<EasebuzzSubMerchant> listAll() {
        return subMerchantRepo.findAll();
    }

    public EasebuzzSubMerchant getById(Long id) {
        return subMerchantRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Sub-merchant not found: " + id));
    }

    public EasebuzzSubMerchant getByRestaurantId(Long restaurantId) {
        return subMerchantRepo.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new RuntimeException("Sub-merchant not found for restaurant: " + restaurantId));
    }

    @Transactional
    public EasebuzzSubMerchant create(Map<String, Object> data, Long restaurantId) {
        if (subMerchantRepo.existsByRestaurantId(restaurantId)) {
            throw new RuntimeException("Sub-merchant already exists for restaurant: " + restaurantId);
        }
        long now = System.currentTimeMillis();
        EasebuzzSubMerchant sm = new EasebuzzSubMerchant();
        sm.setRestaurantId(restaurantId);
        sm.setBusinessName(str(data.get("businessName")));
        sm.setBusinessType(str(data.get("businessType")));
        sm.setPan(str(data.get("pan")));
        sm.setGst(str(data.get("gst")));
        sm.setBankAccountNo(str(data.get("bankAccountNo")));
        sm.setIfsc(str(data.get("ifsc")));
        sm.setBankName(str(data.get("bankName")));
        sm.setBranchName(str(data.get("branchName")));
        sm.setBeneficiaryName(str(data.get("beneficiaryName")));
        sm.setBusinessAddress(str(data.get("businessAddress")));
        sm.setContactEmail(str(data.get("contactEmail")));
        sm.setContactPhone(str(data.get("contactPhone")));
        sm.setCommissionRate(data.containsKey("commissionRate")
                ? new java.math.BigDecimal(data.get("commissionRate").toString()) : java.math.BigDecimal.ZERO);
        if (data.containsKey("upiDeductionLtLimit"))
            sm.setUpiDeductionLtLimit(new java.math.BigDecimal(data.get("upiDeductionLtLimit").toString()));
        if (data.containsKey("dcDeductionGtTwoThousand"))
            sm.setDcDeductionGtTwoThousand(new java.math.BigDecimal(data.get("dcDeductionGtTwoThousand").toString()));
        sm.setStatus("DRAFT");
        sm.setCreatedAt(now);
        sm.setUpdatedAt(now);
        EasebuzzSubMerchant saved = subMerchantRepo.save(sm);
        log.info("Created sub-merchant draft id={} restaurantId={}", saved.getId(), restaurantId);
        return saved;
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }

    /**
     * After creating sub-merchant in Easebuzz Dashboard, admin
     * updates the record with the sub_merchant_id provided by Easebuzz.
     */
    @Transactional
    public EasebuzzSubMerchant assignSubMerchantId(Long id, String subMerchantId) {
        EasebuzzSubMerchant sm = getById(id);
        sm.setSubMerchantId(subMerchantId);
        sm.setStatus("PENDING_KYC");
        sm.setUpdatedAt(System.currentTimeMillis());
        subMerchantRepo.save(sm);
        log.info("Sub-merchant {} assigned Easebuzz ID: {}", id, subMerchantId);
        return sm;
    }

    @Transactional
    public EasebuzzSubMerchant updateStatus(Long id, String newStatus) {
        EasebuzzSubMerchant sm = getById(id);
        sm.setStatus(newStatus);
        sm.setUpdatedAt(System.currentTimeMillis());
        if ("ACTIVE".equals(newStatus)) {
            sm.setKycActivatedAt(System.currentTimeMillis());
        }
        return subMerchantRepo.save(sm);
    }

    @Transactional
    public void processWebhook(Map<String, Object> payload) {
        String rawStatus = (String) payload.getOrDefault("status", "0");
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        String subMerchantId = data != null ? (String) data.get("submerchant_id") : null;
        String kycStatus = data != null ? (String) data.get("status") : null;
        String name = data != null ? (String) data.get("name") : null;
        String email = data != null ? (String) data.get("email") : null;
        long now = System.currentTimeMillis();

        if (subMerchantId == null) {
            log.warn("Sub-merchant webhook missing submerchant_id in data");
            return;
        }

        EasebuzzSubMerchantWebhookEvent event = new EasebuzzSubMerchantWebhookEvent();
        event.setSubMerchantId(subMerchantId);
        event.setEventType("kyc_status");
        event.setRawStatus(kycStatus);
        event.setPayload(payload.toString());
        event.setReceivedAt(now);
        event.setProcessed(false);
        webhookEventRepo.save(event);

        subMerchantRepo.findBySubMerchantId(subMerchantId).ifPresent(sm -> {
            if (kycStatus != null) {
                sm.setKycStatus(kycStatus);
                if ("True".equalsIgnoreCase(kycStatus)) {
                    sm.setStatus("ACTIVE");
                    sm.setKycActivatedAt(now);
                } else if ("False".equalsIgnoreCase(kycStatus)) {
                    sm.setStatus("REJECTED");
                } else if ("Pending".equalsIgnoreCase(kycStatus)) {
                    sm.setStatus("PENDING_KYC");
                }
                sm.setUpdatedAt(now);
                subMerchantRepo.save(sm);
            }
            event.setProcessed(true);
            webhookEventRepo.save(event);
            log.info("Processed sub-merchant KYC webhook subMerchantId={} kycStatus={}", subMerchantId, kycStatus);
        });
    }

    @Transactional
    public EasebuzzSubMerchant updateOnEasebuzz(Long id) {
        EasebuzzSubMerchant sm = getById(id);
        if (sm.getSubMerchantId() == null) {
            throw new RuntimeException("Sub-merchant has no Easebuzz ID. Submit to Easebuzz first.");
        }
        Map result = easebuzzApi.updateSubMerchant(
            sm.getSubMerchantId(), sm.getBusinessName(), sm.getContactEmail(), sm.getContactPhone(),
            sm.getBankAccountNo(), sm.getIfsc(), sm.getBankName(),
            sm.getBeneficiaryName(), sm.getBranchName()
        );
        Boolean apiStatus = (Boolean) result.get("status");
        if (Boolean.TRUE.equals(apiStatus)) {
            sm.setEasebuzzResponse(result.toString());
        } else {
            String error = (String) result.get("error");
            sm.setEasebuzzResponse(error != null ? error : result.toString());
        }
        sm.setUpdatedAt(System.currentTimeMillis());
        subMerchantRepo.save(sm);
        log.info("Sub-merchant {} updated on Easebuzz, status={}", id, apiStatus);
        return sm;
    }

    public Map<String, Object> createSplitLabel(Long id) {
        EasebuzzSubMerchant sm = getById(id);
        if (sm.getSubMerchantId() == null) {
            throw new RuntimeException("Sub-merchant has no Easebuzz ID. Submit to Easebuzz first.");
        }
        String label = "sm_" + sm.getSubMerchantId();
        Map result = easebuzzApi.createSplitLabel(
            sm.getBeneficiaryName(), sm.getBankName(), sm.getBranchName(),
            sm.getIfsc(), sm.getBankAccountNo(), label, null
        );
        if ("success".equals(result.get("status"))) {
            sm.setSplitLabel(label);
            sm.setUpdatedAt(System.currentTimeMillis());
            subMerchantRepo.save(sm);
        }
        return Map.of(
            "status", result.get("status"),
            "label", label,
            "msg", result.getOrDefault("msg", "")
        );
    }

    public Map<String, Object> generateKycAccessKey(Long id) {
        EasebuzzSubMerchant sm = getById(id);
        if (sm.getSubMerchantId() == null) {
            throw new RuntimeException("Sub-merchant has no Easebuzz ID. Submit to Easebuzz first.");
        }
        Map result = easebuzzApi.generateKycAccessKey(
            sm.getSubMerchantId(),
            sm.getBusinessName(),
            sm.getContactEmail(),
            sm.getContactPhone()
        );
        return Map.of(
            "status", result.get("status"),
            "kyc_url", result.getOrDefault("kyc_url", ""),
            "sub_merchant_id", sm.getSubMerchantId()
        );
    }

    @Transactional
    public EasebuzzSubMerchant submitToEasebuzz(Long id) {
        EasebuzzSubMerchant sm = getById(id);
        Map result = easebuzzApi.createSubMerchant(
            sm.getBusinessName(), sm.getContactEmail(), sm.getContactPhone(),
            sm.getBankAccountNo(), sm.getIfsc(), sm.getBankName(),
            sm.getBeneficiaryName(), sm.getBranchName()
        );
        Boolean apiStatus = (Boolean) result.get("status");
        if (Boolean.TRUE.equals(apiStatus)) {
            String subMerchantId = (String) result.get("submerchant_id");
            sm.setSubMerchantId(subMerchantId);
            sm.setStatus("PENDING_KYC");
            sm.setKycSubmittedAt(System.currentTimeMillis());
            sm.setEasebuzzResponse(result.toString());
        } else {
            sm.setStatus("FAILED");
            String error = (String) result.get("error");
            sm.setEasebuzzResponse(error != null ? error : result.toString());
        }
        sm.setUpdatedAt(System.currentTimeMillis());
        subMerchantRepo.save(sm);
        log.info("Sub-merchant {} submitted to Easebuzz, status={}", id, sm.getStatus());
        return sm;
    }
}
