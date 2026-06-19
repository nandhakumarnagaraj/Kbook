package com.khanabook.saas.service;

import com.khanabook.saas.entity.EasebuzzSubMerchant;
import com.khanabook.saas.entity.EasebuzzSubMerchantWebhookEvent;
import com.khanabook.saas.entity.EasebuzzPayout;
import com.khanabook.saas.exception.BusinessRuleException;
import com.khanabook.saas.exception.EntityNotFoundException;
import com.khanabook.saas.repository.EasebuzzSubMerchantRepository;
import com.khanabook.saas.repository.EasebuzzSubMerchantWebhookEventRepository;
import com.khanabook.saas.repository.EasebuzzPayoutRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
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
    private final EasebuzzWireApiClient wireApi;
    private final EasebuzzSubMerchantRepository subMerchantRepo;
    private final EasebuzzSubMerchantWebhookEventRepository webhookEventRepo;
    private final EasebuzzPayoutRepository payoutRepo;
    private final RestaurantProfileRepository restaurantProfileRepo;
    private final PushNotificationService pushNotificationService;

    public List<EasebuzzSubMerchant> listAll() {
        return subMerchantRepo.findAll();
    }

    public EasebuzzSubMerchant getById(Long id) {
        return subMerchantRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("EasebuzzSubMerchant", id));
    }

    public EasebuzzSubMerchant getByRestaurantId(Long restaurantId) {
        return subMerchantRepo.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("EasebuzzSubMerchant", "restaurantId=" + restaurantId));
    }

    @Transactional
    public EasebuzzSubMerchant create(Map<String, Object> data, Long restaurantId) {
        if (subMerchantRepo.existsByRestaurantId(restaurantId)) {
            throw new BusinessRuleException(
                "Sub-merchant already exists for restaurant: " + restaurantId,
                "DUPLICATE_SUB_MERCHANT"
            );
        }
        long now = System.currentTimeMillis();
        EasebuzzSubMerchant sm = new EasebuzzSubMerchant();
        sm.setRestaurantId(restaurantId);
        sm.setBusinessName(str(data.get("businessName")));
        sm.setLegalEntityName(str(data.get("legalEntityName")));
        sm.setBusinessType(str(data.get("businessType")));
        sm.setPan(str(data.get("pan")));
        sm.setGst(str(data.get("gst")));
        sm.setBankAccountNo(str(data.get("bankAccountNo")));
        sm.setIfsc(str(data.get("ifsc")));
        sm.setBankName(str(data.get("bankName")));
        sm.setBranchName(str(data.get("branchName")));
        sm.setBeneficiaryName(str(data.get("beneficiaryName")));
        sm.setBusinessAddress(str(data.get("businessAddress")));
        sm.setState(str(data.get("state")));
        sm.setFssaiNumber(str(data.get("fssaiNumber")));
        if (data.get("fssaiExpiryDate") != null)
            sm.setFssaiExpiryDate(Long.parseLong(data.get("fssaiExpiryDate").toString()));
        sm.setBusinessProof1Type(str(data.get("businessProof1Type")));
        sm.setBusinessProof1Url(str(data.get("businessProof1Url")));
        sm.setBusinessProof2Type(str(data.get("businessProof2Type")));
        sm.setBusinessProof2Url(str(data.get("businessProof2Url")));
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

    /** Matches the proprietorship business types EaseBuzz requires two proofs for. */
    private boolean isProprietorship(String businessType) {
        if (businessType == null) return false;
        String t = businessType.trim().toUpperCase().replace(" ", "_");
        return t.equals("SOLE_PROPRIETORSHIP") || t.equals("PROPRIETORSHIP")
            || t.equals("SOLE_PROPRIETOR") || t.equals("INDIVIDUAL");
    }

    @Transactional
    public EasebuzzSubMerchant assignSubMerchantId(Long id, String subMerchantId) {
        EasebuzzSubMerchant sm = getById(id);
        sm.setSubMerchantId(subMerchantId);
        sm.setStatus("PENDING_KYC");
        sm.setUpdatedAt(System.currentTimeMillis());
        subMerchantRepo.save(sm);
        ensureEasebuzzEnabled(sm.getRestaurantId());
        log.info("Sub-merchant {} assigned Easebuzz ID: {}, easebuzzEnabled set to true for restaurant {}", id, subMerchantId, sm.getRestaurantId());
        
        try {
            pushNotificationService.pushToRestaurant(
                sm.getRestaurantId(),
                "Sub-Merchant Linked",
                "Easebuzz sub-merchant account linked successfully. KYC verification is pending.",
                "kyc",
                String.valueOf(sm.getId()),
                "submerchant",
                java.math.BigDecimal.ZERO
            );
        } catch (Exception e) {
            log.warn("Failed to push submerchant link notification: {}", e.getMessage());
        }
        
        return sm;
    }

    @Transactional
    public EasebuzzSubMerchant updateStatus(Long id, String newStatus) {
        EasebuzzSubMerchant sm = getById(id);
        String oldStatus = sm.getStatus();
        sm.setStatus(newStatus);
        sm.setUpdatedAt(System.currentTimeMillis());
        if ("ACTIVE".equals(newStatus)) {
            sm.setKycActivatedAt(System.currentTimeMillis());
        }
        EasebuzzSubMerchant saved = subMerchantRepo.save(sm);
        
        if (!newStatus.equals(oldStatus)) {
            try {
                pushNotificationService.pushToRestaurant(
                    sm.getRestaurantId(),
                    "KYC Status Updated",
                    "Your sub-merchant status is now: " + newStatus,
                    "kyc",
                    String.valueOf(sm.getId()),
                    "submerchant",
                    java.math.BigDecimal.ZERO
                );
            } catch (Exception e) {
                log.warn("Failed to push KYC status update notification: {}", e.getMessage());
            }
        }
        
        return saved;
    }

    @Transactional
    public EasebuzzSubMerchant update(Long id, Map<String, String> data) {
        EasebuzzSubMerchant sm = getById(id);
        if (data.containsKey("businessName")) sm.setBusinessName(data.get("businessName"));
        if (data.containsKey("legalEntityName")) sm.setLegalEntityName(data.get("legalEntityName"));
        if (data.containsKey("businessType")) sm.setBusinessType(data.get("businessType"));
        if (data.containsKey("pan")) sm.setPan(data.get("pan"));
        if (data.containsKey("gst")) sm.setGst(data.get("gst"));
        if (data.containsKey("bankAccountNo")) sm.setBankAccountNo(data.get("bankAccountNo"));
        if (data.containsKey("ifsc")) sm.setIfsc(data.get("ifsc"));
        if (data.containsKey("bankName")) sm.setBankName(data.get("bankName"));
        if (data.containsKey("branchName")) sm.setBranchName(data.get("branchName"));
        if (data.containsKey("beneficiaryName")) sm.setBeneficiaryName(data.get("beneficiaryName"));
        if (data.containsKey("businessAddress")) sm.setBusinessAddress(data.get("businessAddress"));
        if (data.containsKey("state")) sm.setState(data.get("state"));
        if (data.containsKey("fssaiNumber")) sm.setFssaiNumber(data.get("fssaiNumber"));
        if (data.containsKey("fssaiExpiryDate") && data.get("fssaiExpiryDate") != null)
            sm.setFssaiExpiryDate(Long.parseLong(data.get("fssaiExpiryDate")));
        if (data.containsKey("businessProof1Type")) sm.setBusinessProof1Type(data.get("businessProof1Type"));
        if (data.containsKey("businessProof1Url")) sm.setBusinessProof1Url(data.get("businessProof1Url"));
        if (data.containsKey("businessProof2Type")) sm.setBusinessProof2Type(data.get("businessProof2Type"));
        if (data.containsKey("businessProof2Url")) sm.setBusinessProof2Url(data.get("businessProof2Url"));
        if (data.containsKey("idProofUrl")) sm.setIdProofUrl(data.get("idProofUrl"));
        if (data.containsKey("bankProofUrl")) sm.setBankProofUrl(data.get("bankProofUrl"));
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
    public void updateKycDocumentUrl(Long restaurantId, String docType, String url) {
        EasebuzzSubMerchant sm = subMerchantRepo.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("EasebuzzSubMerchant", "restaurantId=" + restaurantId));
        switch (docType) {
            case "id_proof":
                sm.setIdProofUrl(url);
                break;
            case "bank_proof":
                sm.setBankProofUrl(url);
                break;
            case "business_proof_1":
                sm.setBusinessProof1Url(url);
                break;
            case "business_proof_2":
                sm.setBusinessProof2Url(url);
                break;
            default:
                throw new IllegalArgumentException("Unknown KYC document type: " + docType);
        }
        sm.setUpdatedAt(System.currentTimeMillis());
        subMerchantRepo.save(sm);
        log.info("Updated KYC doc URL type={} for restaurantId={}: {}", docType, restaurantId, url);
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
                
                try {
                    String statusMessage = "True".equalsIgnoreCase(kycStatus) ? "Approved & Activated! 🚀" :
                                           "False".equalsIgnoreCase(kycStatus) ? "Rejected/Needs attention ⚠️" : "Pending Verification ⏳";
                    pushNotificationService.pushToRestaurant(
                        sm.getRestaurantId(),
                        "KYC Status Update",
                        "Your sub-merchant KYC status is now: " + statusMessage,
                        "kyc",
                        String.valueOf(sm.getId()),
                        "submerchant",
                        java.math.BigDecimal.ZERO
                    );
                } catch (Exception e) {
                    log.warn("Failed to push KYC status update notification: {}", e.getMessage());
                }
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
            throw new BusinessRuleException(
                "Sub-merchant has no Easebuzz ID. Submit to Easebuzz first.",
                "MISSING_EASEBUZZ_ID"
            );
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
            throw new BusinessRuleException(
                "Sub-merchant has no Easebuzz ID. Submit to Easebuzz first.",
                "MISSING_EASEBUZZ_ID"
            );
        }
        String label = "sm_" + sm.getSubMerchantId();
        Map<String, Object> result = easebuzzApi.createSplitLabel(
            sm.getBeneficiaryName(), sm.getBankName(), sm.getBranchName(),
            sm.getIfsc(), sm.getBankAccountNo(), label, "100"
        );
        if (toBool(result.get("status"))) {
            sm.setSplitLabel(label);
            sm.setUpdatedAt(System.currentTimeMillis());
            subMerchantRepo.save(sm);
        }
        Object msg = result.get("msg");
        if (msg == null) msg = result.get("error_desc");
        if (msg == null) msg = result.get("error");
        return Map.of(
            "status", result.getOrDefault("status", "failure"),
            "label", label,
            "msg", msg != null ? msg : ""
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
            throw new BusinessRuleException(
                "Sub-merchant has no Easebuzz ID. Submit to Easebuzz first.",
                "MISSING_EASEBUZZ_ID"
            );
        }
        Map<String, Object> result = easebuzzApi.generateKycAccessKey(
            sm.getSubMerchantId(),
            sm.getBusinessName(),
            sm.getContactEmail(),
            sm.getContactPhone()
        );
        // Easebuzz response returns kyc_dashboard_url or msg with the KYC portal URL
        Object kycUrlObj = result.get("kyc_dashboard_url");
        if (kycUrlObj == null || kycUrlObj.toString().isBlank()) {
            kycUrlObj = result.get("msg");
        }
        String kycUrl = kycUrlObj != null ? kycUrlObj.toString() : "";
        if (!kycUrl.isBlank()) {
            sm.setKycPortalUrl(kycUrl);
            sm.setUpdatedAt(System.currentTimeMillis());
            subMerchantRepo.save(sm);
        }
        return Map.of(
            "status", result.getOrDefault("status", "failure"),
            "kyc_url", kycUrl,
            "sub_merchant_id", sm.getSubMerchantId()
        );
    }

    public Map<String, Object> verifyOtp(Long id, String otp) {
        EasebuzzSubMerchant sm = getById(id);
        if (sm.getSubMerchantId() == null) {
            throw new BusinessRuleException("Sub-merchant has no Easebuzz ID.", "NO_EASEBUZZ_ID");
        }
        return easebuzzApi.verifyOtp(sm.getSubMerchantId(), otp);
    }

    public Map<String, Object> resendOtp(Long id) {
        EasebuzzSubMerchant sm = getById(id);
        if (sm.getSubMerchantId() == null) {
            throw new BusinessRuleException("Sub-merchant has no Easebuzz ID.", "NO_EASEBUZZ_ID");
        }
        return easebuzzApi.resendOtp(sm.getSubMerchantId());
    }

    // ============================================================
    // Owner-driven (POS app) onboarding orchestration.
    // Tenant-scoped: the controller resolves restaurantId from TenantContext,
    // so a restaurant owner can only ever act on their own sub-merchant.
    // ============================================================

    /**
     * Onboard the restaurant's sub-merchant directly from the POS app: create a draft if none
     * exists, apply the submitted details, then submit to EaseBuzz. Skips the local business-proof
     * gate because documents are uploaded later on EaseBuzz's hosted KYC portal (no document API).
     * Only valid when nothing has been submitted yet (no record, or DRAFT/FAILED). After a rejection
     * the owner uses {@link #resubmitForRestaurant} instead.
     */
    @Transactional
    public EasebuzzSubMerchant onboardForRestaurant(Long restaurantId, Map<String, Object> data) {
        EasebuzzSubMerchant existing = subMerchantRepo.findByRestaurantId(restaurantId).orElse(null);
        EasebuzzSubMerchant sm;
        if (existing != null) {
            String status = existing.getStatus();
            if (!"DRAFT".equals(status) && !"FAILED".equals(status)) {
                throw new BusinessRuleException(
                    "Onboarding already submitted (status " + status + "). Use resubmit to update details.",
                    "ONBOARDING_ALREADY_SUBMITTED"
                );
            }
            sm = update(existing.getId(), toStringMap(data));
        } else {
            sm = create(data, restaurantId);
        }
        return submitToEasebuzz(sm.getId(), false);
    }

    /**
     * Push corrected details to EaseBuzz after a KYC rejection, from the POS app.
     * The local status stays REJECTED until EaseBuzz re-reviews and sends a new KYC webhook.
     */
    @Transactional
    public EasebuzzSubMerchant resubmitForRestaurant(Long restaurantId, Map<String, Object> data) {
        EasebuzzSubMerchant sm = getByRestaurantId(restaurantId);
        update(sm.getId(), toStringMap(data));
        return updateOnEasebuzz(sm.getId());
    }

    private Map<String, String> toStringMap(Map<String, Object> data) {
        Map<String, String> out = new java.util.HashMap<>();
        for (Map.Entry<String, Object> e : data.entrySet()) {
            out.put(e.getKey(), e.getValue() != null ? e.getValue().toString() : null);
        }
        return out;
    }

    /** Resolve the tenant's sub-merchant DB id, or throw if none exists yet. */
    public Long requireSubMerchantIdForRestaurant(Long restaurantId) {
        return getByRestaurantId(restaurantId).getId();
    }

    public Map<String, Object> initiateOnDemandSettlement(String amount) {
        String requestId = "SETTLE_" + System.currentTimeMillis();
        return easebuzzApi.initiateOnDemandSettlement(requestId, amount);
    }

    @Transactional
    public Map<String, Object> initiatePayout(String amount, Map<String, String> beneficiaryDetails) {
        String requestId = "PAYOUT_" + System.currentTimeMillis();
        
        // Log initiation in DB
        EasebuzzPayout payout = new EasebuzzPayout();
        payout.setMerchantRequestId(requestId);
        payout.setAmount(new java.math.BigDecimal(amount));
        payout.setBeneficiaryName(beneficiaryDetails.get("beneficiary_name"));
        payout.setAccountNumber(beneficiaryDetails.get("beneficiary_account_number"));
        payout.setIfsc(beneficiaryDetails.get("beneficiary_ifsc"));
        payout.setStatus("initiated");
        payout.setCreatedAt(System.currentTimeMillis());
        payout.setUpdatedAt(System.currentTimeMillis());
        
        Long restaurantId = com.khanabook.saas.security.TenantContext.getCurrentTenant();
        payout.setRestaurantId(restaurantId != null ? restaurantId : 0L);
        payoutRepo.save(payout);

        Map<String, Object> result = easebuzzApi.initiatePayout(requestId, amount, beneficiaryDetails);
        
        if (toBool(result.get("status"))) {
            Map data = (Map) result.get("data");
            if (data != null && data.containsKey("payout_id")) {
                payout.setPayoutId(data.get("payout_id").toString());
            }
            payout.setStatus("pending");
        } else {
            payout.setStatus("failed");
            payout.setErrorMessage(str(result.get("error")));
        }
        payout.setUpdatedAt(System.currentTimeMillis());
        payoutRepo.save(payout);
        
        return result;
    }

    private static boolean toBool(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        String s = value.toString().trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "success".equalsIgnoreCase(s);
    }

    public Map<String, Object> cancelTransaction(String txnid, String amount) {
        return easebuzzApi.cancelTransaction(txnid, amount);
    }

    public Map<String, Object> retrieveSettlements(String date) {
        return easebuzzApi.retrieveSettlements(date);
    }

    @Transactional
    public void ensureEasebuzzEnabled(Long restaurantId) {
        restaurantProfileRepo.findByRestaurantId(restaurantId).ifPresent(profile -> {
            if (profile.getEasebuzzEnabled() == null || !profile.getEasebuzzEnabled()) {
                profile.setEasebuzzEnabled(true);
                long now = System.currentTimeMillis();
                profile.setUpdatedAt(now);
                profile.setServerUpdatedAt(now);
                profile.setDeviceId("server");
                restaurantProfileRepo.save(profile);
            }
        });
    }

    @Transactional
    public void hardDeleteSubMerchant(Long id) {
        subMerchantRepo.findById(id).ifPresentOrElse(sm -> {
            // Clean up webhook events associated with this sub-merchant's Easebuzz ID
            if (sm.getSubMerchantId() != null) {
                var events = webhookEventRepo.findBySubMerchantIdOrderByReceivedAtDesc(sm.getSubMerchantId());
                if (!events.isEmpty()) {
                    webhookEventRepo.deleteAll(events);
                }
            }
            subMerchantRepo.delete(sm);
            log.info("Sub-merchant {} hard deleted.", id);
        }, () -> log.info("Sub-merchant {} already deleted, skipping.", id));
    }

    /**
     * Public delete — only allows removing DRAFT or FAILED records.
     * PENDING_KYC / ACTIVE sub-merchants are live on Easebuzz and cannot be deleted locally.
     */
    @Transactional
    public void delete(Long id) {
        EasebuzzSubMerchant sm = getById(id);
        String status = sm.getStatus();
        if (!"DRAFT".equals(status) && !"FAILED".equals(status)) {
            throw new BusinessRuleException(
                "Cannot delete sub-merchant with status " + status + ". Only DRAFT or FAILED records can be deleted.",
                "DELETE_NOT_ALLOWED"
            );
        }
        hardDeleteSubMerchant(id);
        log.info("Sub-merchant {} deleted (status was {}).", id, status);
    }

    @Transactional
    public EasebuzzSubMerchant submitToEasebuzz(Long id) {
        return submitToEasebuzz(id, true);
    }

    /**
     * Submit a sub-merchant to EaseBuzz.
     *
     * @param requireBusinessProofs when true (admin/web flow) proprietorships must already have two
     *        business-proof document URLs stored locally before submission. The owner-driven POS flow
     *        passes false: EaseBuzz has no document-ingest API, so KYC documents (including the two
     *        proprietorship proofs) are uploaded later on EaseBuzz's hosted KYC portal, not stored here.
     *        The FSSAI license number is always required because it is submitted as text via the API.
     */
    @Transactional
    public EasebuzzSubMerchant submitToEasebuzz(Long id, boolean requireBusinessProofs) {
        EasebuzzSubMerchant sm = getById(id);
        // EaseBuzz compliance: a valid FSSAI license is mandatory for food merchants.
        if (sm.getFssaiNumber() == null || sm.getFssaiNumber().isBlank()) {
            throw new BusinessRuleException(
                "A valid FSSAI license number is required before onboarding to EaseBuzz.",
                "FSSAI_REQUIRED"
            );
        }
        // EaseBuzz CPV: proprietorship entities must provide two valid business proofs.
        if (requireBusinessProofs && isProprietorship(sm.getBusinessType())) {
            boolean proof1 = sm.getBusinessProof1Url() != null && !sm.getBusinessProof1Url().isBlank();
            boolean proof2 = sm.getBusinessProof2Url() != null && !sm.getBusinessProof2Url().isBlank();
            if (!proof1 || !proof2) {
                throw new BusinessRuleException(
                    "Proprietorship entities require two valid business proof documents for CPV.",
                    "BUSINESS_PROOFS_REQUIRED"
                );
            }
        }
        Map<String, Object> result = easebuzzApi.createSubMerchant(
            sm.getBusinessName(), sm.getContactEmail(), sm.getContactPhone(),
            sm.getBankAccountNo(), sm.getIfsc(), sm.getBankName(),
            sm.getBeneficiaryName(), sm.getBranchName(),
            sm.getBusinessType(), sm.getPan(), sm.getGst(),
            sm.getBusinessAddress(),
            sm.getLegalEntityName(), sm.getState(), sm.getFssaiNumber()
        );
        Object statusObj = result != null ? result.get("status") : null;
        boolean apiStatus = EasebuzzApiClient.toBool(statusObj);
        if (apiStatus && result != null) {
            Object subMerchantIdObj = result.get("submerchant_id");
            String subMerchantId = subMerchantIdObj != null ? subMerchantIdObj.toString() : null;
            sm.setSubMerchantId(subMerchantId);
            sm.setStatus("PENDING_KYC");
            sm.setKycSubmittedAt(System.currentTimeMillis());
            sm.setEasebuzzResponse(result.toString());
            ensureEasebuzzEnabled(sm.getRestaurantId());
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

    // ============================================================
    // WIRE Platform API Methods
    // ============================================================

    /**
     * Lookup sub-merchant details on Easebuzz WIRE platform by email address.
     */
    public Map<String, Object> wireLookupByEmail(String email) {
        return wireApi.getSubMerchantByEmail(email);
    }

    /**
     * Lookup sub-merchant details on Easebuzz WIRE platform by sub-merchant key.
     */
    public Map<String, Object> wireLookupByKey(String subMerchantKey) {
        return wireApi.getSubMerchantByKey(subMerchantKey);
    }

    /**
     * Get KYC profile URL for a sub-merchant (retrieves existing URL, does not create a new one).
     */
    public Map<String, Object> wireGetKycProfileUrl(Long id) {
        EasebuzzSubMerchant sm = getById(id);
        if (sm.getSubMerchantId() == null) {
            throw new BusinessRuleException("Sub-merchant has no Easebuzz ID. Submit to Easebuzz first.", "NO_EASEBUZZ_ID");
        }
        return wireApi.getKycProfileUrl(sm.getSubMerchantId());
    }

    /**
     * Configure webhooks for InstaCollect (QR) on the WIRE platform.
     */
    public Map<String, Object> wireConfigureInstaCollectWebhook(
            String subMerchantId,
            String merchantEmail,
            String eventType,
            String url,
            String intervalUnit,
            int intervalValue,
            int maxAttempts) {
        return wireApi.configureInstaCollectWebhook(
                subMerchantId, merchantEmail, eventType, url,
                intervalUnit, intervalValue, maxAttempts);
    }

    /**
     * Configure webhooks for WIRE (Payouts) on the WIRE platform.
     */
    public Map<String, Object> wireConfigurePayoutWebhook(
            String merchantKey,
            String eventType,
            String url,
            String intervalUnit,
            int intervalValue,
            int maxAttempts) {
        return wireApi.configurePayoutWebhook(
                merchantKey, eventType, url,
                intervalUnit, intervalValue, maxAttempts);
    }
}
