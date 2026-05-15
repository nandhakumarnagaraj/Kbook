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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SubMerchantService {

    private static final Logger log = LoggerFactory.getLogger(SubMerchantService.class);
    private final EasebuzzSubMerchantRepository subMerchantRepo;
    private final EasebuzzSubMerchantWebhookEventRepository webhookEventRepo;
    private final EasebuzzApiClient easebuzzApi;

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
    public EasebuzzSubMerchant create(Map<String, String> data, Long restaurantId) {
        if (subMerchantRepo.existsByRestaurantId(restaurantId)) {
            throw new RuntimeException("Sub-merchant already exists for restaurant: " + restaurantId);
        }
        long now = System.currentTimeMillis();
        EasebuzzSubMerchant sm = new EasebuzzSubMerchant();
        sm.setRestaurantId(restaurantId);
        sm.setBusinessName(data.get("businessName"));
        sm.setBusinessType(data.get("businessType"));
        sm.setPan(data.get("pan"));
        sm.setGst(data.get("gst"));
        sm.setBankAccountNo(data.get("bankAccountNo"));
        sm.setIfsc(data.get("ifsc"));
        sm.setBeneficiaryName(data.get("beneficiaryName"));
        sm.setBusinessAddress(data.get("businessAddress"));
        sm.setContactEmail(data.get("contactEmail"));
        sm.setContactPhone(data.get("contactPhone"));
        sm.setCommissionRate(data.containsKey("commissionRate")
                ? new java.math.BigDecimal(data.get("commissionRate")) : java.math.BigDecimal.ZERO);
        sm.setStatus("DRAFT");
        sm.setCreatedAt(now);
        sm.setUpdatedAt(now);
        EasebuzzSubMerchant saved = subMerchantRepo.save(sm);
        log.info("Created sub-merchant draft id={} restaurantId={}", saved.getId(), restaurantId);
        return saved;
    }

    @Transactional
    public EasebuzzSubMerchant registerWithEasebuzz(Long id) {
        EasebuzzSubMerchant sm = getById(id);
        Map<String, String> easebuzzData = new HashMap<>();
        easebuzzData.put("merchant_key", "");
        easebuzzData.put("business_name", sm.getBusinessName());
        easebuzzData.put("business_type", sm.getBusinessType());
        easebuzzData.put("pan", sm.getPan());
        easebuzzData.put("gst", sm.getGst() != null ? sm.getGst() : "");
        easebuzzData.put("bank_account_no", sm.getBankAccountNo());
        easebuzzData.put("ifsc", sm.getIfsc());
        easebuzzData.put("beneficiary_name", sm.getBeneficiaryName());
        easebuzzData.put("business_address", sm.getBusinessAddress());
        easebuzzData.put("contact_email", sm.getContactEmail());
        easebuzzData.put("contact_phone", sm.getContactPhone());

        Map result = easebuzzApi.createSubMerchant(easebuzzData);
        String status = (String) result.getOrDefault("status", "failure");
        if ("success".equalsIgnoreCase(status)) {
            sm.setSubMerchantId((String) result.get("sub_merchant_id"));
            sm.setStatus("PENDING_KYC");
            sm.setEasebuzzResponse(result.toString());
        } else {
            sm.setStatus("FAILED");
            sm.setEasebuzzResponse(result.toString());
        }
        sm.setUpdatedAt(System.currentTimeMillis());
        subMerchantRepo.save(sm);
        log.info("Sub-merchant {} registration status={}", id, sm.getStatus());
        return sm;
    }

    @Transactional
    public EasebuzzSubMerchant generateKyc(Long id) {
        EasebuzzSubMerchant sm = getById(id);
        if (sm.getSubMerchantId() == null) {
            throw new RuntimeException("Sub-merchant not registered with Easebuzz yet");
        }
        Map<String, String> data = Map.of(
            "merchant_key", "",
            "sub_merchant_id", sm.getSubMerchantId()
        );
        Map result = easebuzzApi.generateKycAccessKey(data);
        String status = (String) result.getOrDefault("status", "failure");
        if ("success".equalsIgnoreCase(status)) {
            sm.setKycPortalUrl((String) result.get("kyc_portal_url"));
            sm.setKycSubmittedAt(System.currentTimeMillis());
            sm.setStatus("KYC_SUBMITTED");
        }
        sm.setEasebuzzResponse(result.toString());
        sm.setUpdatedAt(System.currentTimeMillis());
        subMerchantRepo.save(sm);
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
    public void processWebhook(Map<String, String> payload) {
        String subMerchantId = payload.get("sub_merchant_id");
        String eventType = payload.getOrDefault("event_type", "UNKNOWN");
        String rawStatus = payload.get("status");
        long now = System.currentTimeMillis();

        EasebuzzSubMerchantWebhookEvent event = new EasebuzzSubMerchantWebhookEvent();
        event.setSubMerchantId(subMerchantId);
        event.setEventType(eventType);
        event.setRawStatus(rawStatus);
        event.setPayload(payload.toString());
        event.setReceivedAt(now);
        event.setProcessed(false);
        webhookEventRepo.save(event);

        subMerchantRepo.findBySubMerchantId(subMerchantId).ifPresent(sm -> {
            if ("kyc_status".equals(eventType) && rawStatus != null) {
                sm.setKycStatus(rawStatus);
                if ("ACTIVE".equals(rawStatus)) {
                    sm.setStatus("ACTIVE");
                    sm.setKycActivatedAt(now);
                } else if ("REJECTED".equals(rawStatus)) {
                    sm.setStatus("REJECTED");
                }
                sm.setUpdatedAt(now);
                subMerchantRepo.save(sm);
            } else if ("sub_merchant_status".equals(eventType) && rawStatus != null) {
                sm.setStatus(rawStatus);
                sm.setUpdatedAt(now);
                subMerchantRepo.save(sm);
            }
            event.setProcessed(true);
            webhookEventRepo.save(event);
            log.info("Processed sub-merchant webhook event={} subMerchantId={} status={}", eventType, subMerchantId, rawStatus);
        });
    }
}
