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
        Object commissionVal = data.get("commissionRate");
        sm.setCommissionRate(commissionVal != null
                ? new java.math.BigDecimal(commissionVal.toString()) : java.math.BigDecimal.ZERO);
        if (data.containsKey("upiDeductionLtLimit") && data.get("upiDeductionLtLimit") != null)
            sm.setUpiDeductionLtLimit(new java.math.BigDecimal(data.get("upiDeductionLtLimit").toString()));
        if (data.containsKey("dcDeductionGtTwoThousand") && data.get("dcDeductionGtTwoThousand") != null)
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
    public EasebuzzSubMerchant update(Long id, Map<String, String> data) {
        EasebuzzSubMerchant sm = getById(id);
        if (data.containsKey("businessName")) sm.setBusinessName(data.get("businessName"));
        if (data.containsKey("businessType")) sm.setBusinessType(data.get("businessType"));
        if (data.containsKey("pan")) sm.setPan(data.get("pan"));
        if (data.containsKey("gst")) sm.setGst(data.get("gst"));
        if (data.containsKey("bankAccountNo")) sm.setBankAccountNo(data.get("bankAccountNo"));
        if (data.containsKey("ifsc")) sm.setIfsc(data.get("ifsc"));
        if (data.containsKey("bankName")) sm.setBankName(data.get("bankName"));
        if (data.containsKey("branchName")) sm.setBranchName(data.get("branchName"));
        if (data.containsKey("beneficiaryName")) sm.setBeneficiaryName(data.get("beneficiaryName"));
        if (data.containsKey("businessAddress")) sm.setBusinessAddress(data.get("businessAddress"));
        if (data.containsKey("contactEmail")) sm.setContactEmail(data.get("contactEmail"));
        if (data.containsKey("contactPhone")) sm.setContactPhone(data.get("contactPhone"));
        if (data.containsKey("commissionRate") && data.get("commissionRate") != null)
            sm.setCommissionRate(new java.math.BigDecimal(data.get("commissionRate")));
        if (data.containsKey("upiDeductionLtLimit") && data.get("upiDeductionLtLimit") != null)
            sm.setUpiDeductionLtLimit(new java.math.BigDecimal(data.get("upiDeductionLtLimit")));
        if (data.containsKey("dcDeductionGtTwoThousand") && data.get("dcDeductionGtTwoThousand") != null)
            sm.setDcDeductionGtTwoThousand(new java.math.BigDecimal(data.get("dcDeductionGtTwoThousand")));
        sm.setUpdatedAt(System.currentTimeMillis());
        return subMerchantRepo.save(sm);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void processWebhook(Map<String, Object> payload) {
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        String subMerchantId = data != null ? (String) data.get("submerchant_id") : null;
        String kycStatus = data != null ? (String) data.get("status") : null;
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
                    sm.setStatus("KYC_SUBMITTED");
                    sm.setKycSubmittedAt(System.currentTimeMillis());
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
        Map<String, Object> result = easebuzzApi.updateSubMerchant(
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
        Map<String, Object> result = easebuzzApi.createSplitLabel(
            sm.getBeneficiaryName(), sm.getBankName(), sm.getBranchName(),
            sm.getIfsc(), sm.getBankAccountNo(), label, null
        );
        if ("success".equals(result.get("status"))) {
            sm.setSplitLabel(label);
            sm.setUpdatedAt(System.currentTimeMillis());
            subMerchantRepo.save(sm);
        }
        return Map.of(
            "status", result.getOrDefault("status", "failure"),
            "label", label,
            "msg", result.getOrDefault("msg", "")
        );
    }

    public Map<String, Object> retrieveTransactionSplit(String merchantRequestId) {
        Map<String, Object> result = easebuzzApi.retrieveTransactionSplit(merchantRequestId);
        return Map.of(
            "status", result.getOrDefault("status", "failure"),
            "merchant_request_id", result.getOrDefault("merchant_request_id", merchantRequestId),
            "split_configuration", result.getOrDefault("split_configuration", java.util.Collections.emptyList())
        );
    }

    public Map<String, Object> generateKycAccessKey(Long id) {
        EasebuzzSubMerchant sm = getById(id);
        if (sm.getSubMerchantId() == null) {
            throw new RuntimeException("Sub-merchant has no Easebuzz ID. Submit to Easebuzz first.");
        }
        Map<String, Object> result = easebuzzApi.generateKycAccessKey(
            sm.getSubMerchantId(),
            sm.getBusinessName(),
            sm.getContactEmail(),
            sm.getContactPhone()
        );
        String kycUrl = (String) result.getOrDefault("kyc_url", "");
        if (kycUrl != null && !kycUrl.isBlank()) {
            sm.setKycPortalUrl(kycUrl);
            sm.setUpdatedAt(System.currentTimeMillis());
            subMerchantRepo.save(sm);
        }
        return Map.of(
            "status", result.getOrDefault("status", "failure"),
            "kyc_url", kycUrl != null ? kycUrl : "",
            "sub_merchant_id", sm.getSubMerchantId()
        );
    }

    public Map<String, Object> verifyOtp(Long id, String otp) {
        EasebuzzSubMerchant sm = getById(id);
        if (sm.getSubMerchantId() == null) {
            throw new RuntimeException("Sub-merchant has no Easebuzz ID.");
        }
        return easebuzzApi.verifyOtp(sm.getSubMerchantId(), otp);
    }

    public Map<String, Object> resendOtp(Long id) {
        EasebuzzSubMerchant sm = getById(id);
        if (sm.getSubMerchantId() == null) {
            throw new RuntimeException("Sub-merchant has no Easebuzz ID.");
        }
        return easebuzzApi.resendOtp(sm.getSubMerchantId());
    }

    public Map<String, Object> initiateOnDemandSettlement(String amount) {
        String requestId = "SETTLE_" + System.currentTimeMillis();
        return easebuzzApi.initiateOnDemandSettlement(requestId, amount);
    }

    public Map<String, Object> initiatePayout(String amount, Map<String, String> beneficiaryDetails) {
        String requestId = "PAYOUT_" + System.currentTimeMillis();
        return easebuzzApi.initiatePayout(requestId, amount, beneficiaryDetails);
    }

    public Map<String, Object> retrieveSettlements(String date) {
        return easebuzzApi.retrieveSettlements(date);
    }

    @Transactional
    public void deleteSubMerchant(Long id) {
        subMerchantRepo.deleteById(id);
        log.info("Sub-merchant {} deleted via dev-refresh.", id);
    }

    @Transactional
    public EasebuzzSubMerchant submitToEasebuzz(Long id) {
        EasebuzzSubMerchant sm = getById(id);
        Map<String, Object> result = easebuzzApi.createSubMerchant(
            sm.getBusinessName(), sm.getContactEmail(), sm.getContactPhone(),
            sm.getBankAccountNo(), sm.getIfsc(), sm.getBankName(),
            sm.getBeneficiaryName(), sm.getBranchName(),
            sm.getBusinessType(), sm.getPan(), sm.getGst(),
            sm.getBusinessAddress()
        );
        Object statusObj = result != null ? result.get("status") : null;
        boolean apiStatus = statusObj instanceof Boolean ? (Boolean) statusObj : false;
        if (apiStatus && result != null) {
            Object subMerchantIdObj = result.get("submerchant_id");
            String subMerchantId = subMerchantIdObj != null ? subMerchantIdObj.toString() : null;
            sm.setSubMerchantId(subMerchantId);
            sm.setStatus("PENDING_KYC");
            sm.setKycSubmittedAt(System.currentTimeMillis());
            sm.setEasebuzzResponse(result.toString());
        } else {
            sm.setStatus("FAILED");
            Object errorObj = result != null ? result.get("error") : null;
            String error = errorObj != null ? errorObj.toString() : (result != null ? result.toString() : "Unknown error");
            log.error("Easebuzz submission failed for smId={}: {}", id, error);
            sm.setEasebuzzResponse(error);
        }
        sm.setUpdatedAt(System.currentTimeMillis());
        subMerchantRepo.save(sm);
        log.info("Sub-merchant {} submitted to Easebuzz, status={}", id, sm.getStatus());
        return sm;
    }
}
