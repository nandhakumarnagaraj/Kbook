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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        try {
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
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Duplicate sub-merchant creation attempt for restaurantId={}, returning existing", restaurantId);
            return getByRestaurantId(restaurantId);
        }
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
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

    /**
     * Sentinel stored in the {@code *_key} columns to mark a document that was
     * delivered to Easebuzz's servers. It is NOT a local storage key: the file
     * itself lives on Easebuzz (upload_kyc_documents), so the download endpoints
     * must not try to resolve it from disk.
     */
    public static final String EASEBUZZ_HOSTED_MARKER = "EASEBUZZ_HOSTED";

    private static final long KYC_FILE_MAX_BYTES = 10L * 1024 * 1024;
    private static final long KYC_FILE_MIN_BYTES = 64L;

    /**
     * Uploads a KYC/address proof document directly to Easebuzz via
     * {@code POST /submerchant/v1/upload_kyc_documents} (multipart + SHA-256).
     * Per the Easebuzz aggregator model the document is stored on Easebuzz's
     * servers — KhanaBook keeps only submission metadata, never a local PII copy.
     */
    @Transactional
    public Map<String, Object> submitKycDocument(Long restaurantId, String docType, String proofType, MultipartFile file) {
        EasebuzzSubMerchant sm = subMerchantRepo.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("EasebuzzSubMerchant", "restaurantId=" + restaurantId));
        if (sm.getSubMerchantId() == null || sm.getSubMerchantId().isBlank()) {
            throw new BusinessRuleException("Restaurant is not registered with Easebuzz yet. Complete registration first.",
                    "sub_merchant_not_registered");
        }
        String documentType = easebuzzDocumentType(docType);
        if (documentType == null) {
            throw new IllegalArgumentException("Unknown KYC document type: " + docType);
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        if (file.getSize() < KYC_FILE_MIN_BYTES) {
            throw new IllegalArgumentException("Uploaded file is too small to be a valid document");
        }
        if (file.getSize() > KYC_FILE_MAX_BYTES) {
            throw new IllegalArgumentException("File too large; max " + (KYC_FILE_MAX_BYTES / (1024 * 1024)) + " MB");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Could not read uploaded KYC document", e);
        }

        log.info("Submitting KYC document restaurantId={} docType={} easebuzzType={} subMerchant={}",
                restaurantId, docType, documentType, sm.getSubMerchantId());
        Map<String, Object> result = easebuzzApi.uploadKycDocument(sm.getSubMerchantId(), documentType, bytes, sanitizeFilename(file.getOriginalFilename()));

        if (!isTruthy(result.get("status"))) {
            String error = result.get("error") != null ? result.get("error").toString() : "Easebuzz rejected the document";
            log.warn("Easebuzz KYC upload rejected restaurantId={} docType={} error={}", restaurantId, docType, error);
            throw new BusinessRuleException("Easebuzz could not accept the document: " + error, "kyc_upload_rejected");
        }

        switch (docType) {
            case "id_proof" -> sm.setIdProofKey(EASEBUZZ_HOSTED_MARKER);
            case "bank_proof" -> sm.setBankProofKey(EASEBUZZ_HOSTED_MARKER);
            case "business_proof_1" -> {
                sm.setBusinessProof1Key(EASEBUZZ_HOSTED_MARKER);
                if (proofType != null && !proofType.isBlank()) {
                    sm.setBusinessProof1Type(proofType.trim().toUpperCase());
                }
            }
            case "business_proof_2" -> {
                sm.setBusinessProof2Key(EASEBUZZ_HOSTED_MARKER);
                if (proofType != null && !proofType.isBlank()) {
                    sm.setBusinessProof2Type(proofType.trim().toUpperCase());
                }
            }
            default -> throw new IllegalArgumentException("Unknown KYC document type: " + docType);
        }
        if (sm.getKycSubmittedAt() == null) {
            sm.setKycSubmittedAt(System.currentTimeMillis());
        }
        sm.setEasebuzzResponse("KYC_UPLOADED:" + documentType);
        sm.setUpdatedAt(System.currentTimeMillis());
        subMerchantRepo.save(sm);
        log.info("KYC document accepted by Easebuzz restaurantId={} docType={} — no local copy stored", restaurantId, docType);
        return Map.of("status", "success", "subMerchantId", sm.getSubMerchantId(), "documentType", documentType);
    }

    private static String easebuzzDocumentType(String docType) {
        return switch (docType) {
            case "id_proof" -> "ID_PROOF";
            case "bank_proof" -> "BANK_PROOF";
            case "business_proof_1", "business_proof_2" -> "ADDRESS_PROOF";
            default -> null;
        };
    }

    private static boolean isTruthy(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(o.toString()) || "success".equalsIgnoreCase(o.toString());
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) return "document.pdf";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public static String resolveSubMerchantId(Map<String, Object> data) {
        if (data == null) return null;
        if (data.containsKey("submerchant_id") && data.get("submerchant_id") != null) {
            return data.get("submerchant_id").toString();
        } else if (data.containsKey("sub_merchant_id") && data.get("sub_merchant_id") != null) {
            return data.get("sub_merchant_id").toString();
        } else if (data.containsKey("id") && data.get("id") != null) {
            return data.get("id").toString();
        }
        return null;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void processWebhook(Map<String, Object> payload) {
        Object eventTypeObj = payload.get("event");
        String eventType = eventTypeObj != null ? eventTypeObj.toString() : "kyc_status";

        @SuppressWarnings("unchecked")
        Map<String, Object> data = payload.get("data") instanceof Map ? (Map<String, Object>) payload.get("data") : null;
        if (data == null) {
            data = payload;
        }

        String subMerchantId = resolveSubMerchantId(data);

        String email = data.containsKey("email") && data.get("email") != null ? data.get("email").toString() : null;

        Object kycStatusObj = data.containsKey("kyc_status") ? data.get("kyc_status") : data.get("status");
        String kycStatus = kycStatusObj != null ? kycStatusObj.toString() : null;
        String kycProfileStatus = data.containsKey("kyc_profile_status") && data.get("kyc_profile_status") != null
                ? data.get("kyc_profile_status").toString() : null;
        String kycUrl = data.containsKey("kyc_url") && data.get("kyc_url") != null ? data.get("kyc_url").toString() : null;

        long now = System.currentTimeMillis();

        Optional<EasebuzzSubMerchant> smOpt = Optional.empty();
        if (subMerchantId != null && !subMerchantId.isBlank()) {
            smOpt = subMerchantRepo.findBySubMerchantId(subMerchantId);
        }
        if (smOpt.isEmpty() && email != null && !email.isBlank()) {
            smOpt = subMerchantRepo.findByContactEmail(email);
        }

        if (smOpt.isEmpty()) {
            log.warn("Sub-merchant webhook could not resolve sub-merchant (subMerchantId={}, email={})", subMerchantId, email);
            return;
        }

        EasebuzzSubMerchant sm = smOpt.get();
        if (subMerchantId != null && !subMerchantId.isBlank() && !subMerchantId.equalsIgnoreCase(sm.getSubMerchantId())) {
            log.info("Updating local sub-merchant ID {} to official Easebuzz ID {}", sm.getSubMerchantId(), subMerchantId);
            sm.setSubMerchantId(subMerchantId);
        }

        EasebuzzSubMerchantWebhookEvent event = new EasebuzzSubMerchantWebhookEvent();
        event.setSubMerchantId(sm.getSubMerchantId() != null ? sm.getSubMerchantId() : subMerchantId);
        event.setEventType(eventType);
        event.setRawStatus(kycProfileStatus != null ? kycProfileStatus : kycStatus);
        event.setPayload(payload.toString());
        event.setReceivedAt(now);
        event.setProcessed(false);
        webhookEventRepo.save(event);

        if (kycUrl != null && !kycUrl.isBlank()) {
            sm.setKycPortalUrl(kycUrl);
        }

        if (kycProfileStatus != null || kycStatus != null) {
            if ("Completed".equalsIgnoreCase(kycProfileStatus) || "True".equalsIgnoreCase(kycStatus) || Boolean.TRUE.equals(kycStatusObj)) {
                sm.setStatus("ACTIVE");
                sm.setKycStatus("True");
                sm.setKycActivatedAt(now);
            } else if ("CPV_PENDING".equalsIgnoreCase(kycProfileStatus)) {
                sm.setStatus("CPV_PENDING");
                sm.setKycStatus("Pending");
            } else if ("Rejected".equalsIgnoreCase(kycProfileStatus) || "False".equalsIgnoreCase(kycStatus) || Boolean.FALSE.equals(kycStatusObj)) {
                sm.setStatus("REJECTED");
                sm.setKycStatus("False");
            } else if ("Pending".equalsIgnoreCase(kycProfileStatus) || "Pending".equalsIgnoreCase(kycStatus)) {
                sm.setStatus("KYC_SUBMITTED");
                sm.setKycStatus("Pending");
                sm.setKycSubmittedAt(now);
            }

            // Store virtual_account details from KYC approval webhook
            @SuppressWarnings("unchecked")
            Map<String, Object> virtualAccount = data.get("virtual_account") instanceof Map ? (Map<String, Object>) data.get("virtual_account") : null;
            if (virtualAccount != null) {
                if (virtualAccount.get("id") != null) sm.setVirtualAccountId(virtualAccount.get("id").toString());
                if (virtualAccount.get("account_number") != null) sm.setVirtualAccountNumber(virtualAccount.get("account_number").toString());
                if (virtualAccount.get("ifsc") != null) sm.setVirtualAccountIfsc(virtualAccount.get("ifsc").toString());
                if (virtualAccount.get("bank_name") != null) sm.setVirtualAccountBank(virtualAccount.get("bank_name").toString());
                if (virtualAccount.get("status") != null) {
                    log.info("Sub-merchant {} virtual account status={}", sm.getSubMerchantId(), virtualAccount.get("status"));
                }
            }

            sm.setUpdatedAt(now);
            subMerchantRepo.save(sm);

            try {
                String statusMessage;
                if ("ACTIVE".equals(sm.getStatus())) {
                    statusMessage = "Approved & Activated! 🚀";
                } else if ("CPV_PENDING".equals(sm.getStatus())) {
                    statusMessage = "CPV Verification in Progress ⏳";
                } else if ("REJECTED".equals(sm.getStatus())) {
                    statusMessage = "Rejected/Needs attention ⚠️";
                } else {
                    statusMessage = "Pending Verification ⏳";
                }

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
            rotateSplitLabelIfBankChanged(sm);
        } else {
            String error = (String) result.get("error");
            sm.setEasebuzzResponse(error != null ? error : result.toString());
        }
        sm.setUpdatedAt(System.currentTimeMillis());
        subMerchantRepo.save(sm);
        log.info("Sub-merchant {} updated on Easebuzz, status={}", id, apiStatus);
        return sm;
    }

    /**
     * If a split label is already registered and the restaurant's bank account or
     * IFSC no longer matches the snapshot that label was registered against, register
     * a new versioned label (sm_<id>_vN) pointing at the new bank. Easebuzz has no
     * label-update API (ERA Q5), so rotation is the only safe path; old labels may be
     * retired via support. Runs in reachable state only — best-effort, never throws.
     */
    private void rotateSplitLabelIfBankChanged(EasebuzzSubMerchant sm) {
        if (sm.getSplitLabel() == null || sm.getSplitLabel().isBlank()) {
            return;
        }
        if (sm.getBankAccountNo() == null || sm.getIfsc() == null) {
            return;
        }
        String currentSnapshot = sm.getBankAccountNo() + "|" + sm.getIfsc();
        if (currentSnapshot.equals(sm.getSplitLabelBankSnapshot())) {
            return;
        }
        log.info("Bank change detected for sub-merchant {} (label {}): {} -> {}",
            sm.getSubMerchantId(), sm.getSplitLabel(), sm.getSplitLabelBankSnapshot(), currentSnapshot);
        try {
            createSplitLabel(sm.getId());
            log.info("Rotated split label for sub-merchant {} to {}", sm.getSubMerchantId(), sm.getSplitLabel());
        } catch (Exception e) {
            log.error("Split label rotation failed for sub-merchant {}: {}", sm.getSubMerchantId(), e.getMessage());
        }
    }

    public Map<String, Object> createSplitLabel(Long id) {
        EasebuzzSubMerchant sm = getById(id);
        if (sm.getSubMerchantId() == null) {
            throw new BusinessRuleException(
                "Sub-merchant has no Easebuzz ID. Submit to Easebuzz first.",
                "MISSING_EASEBUZZ_ID"
            );
        }
        int version = sm.getSplitLabelVersion() != null ? sm.getSplitLabelVersion() : 0;
        String base = "sm_" + sm.getSubMerchantId();
        String label = version == 0 ? base : base + "_v" + version;
        Map<String, Object> result = easebuzzApi.createSplitLabel(
            sm.getBeneficiaryName(), sm.getBankName(), sm.getBranchName(),
            sm.getIfsc(), sm.getBankAccountNo(), label, "100"
        );
        if (toBool(result.get("status"))) {
            sm.setSplitLabel(label);
            sm.setSplitLabelVersion(version + 1);
            sm.setSplitLabelBankSnapshot(sm.getBankAccountNo() + "|" + sm.getIfsc());
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

    @Deprecated
    public Map<String, Object> cancelTransaction(String txnid, String amount) {
        return easebuzzApi.cancelTransaction(txnid, amount);
    }

    public Map<String, Object> retrieveSettlements(String date) {
        return easebuzzApi.retrieveSettlements(date);
    }

    public Map<String, Object> retrieveSettlements(String startDate, String endDate, String subMerchantId) {
        return easebuzzApi.retrieveSettlements(startDate, endDate, subMerchantId);
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
        // EaseBuzz CPV: the legal entity name (as registered with PAN/GST) must be
        // submitted explicitly. Silently falling back to the display/trade name is the
        // root cause of CPV name-mismatch (negative report), so block submission here
        // rather than let EasebuzzApiClient substitute the trade name.
        if (sm.getLegalEntityName() == null || sm.getLegalEntityName().isBlank()) {
            throw new BusinessRuleException(
                "The registered legal entity name (matching PAN/GST) is required before onboarding to EaseBuzz.",
                "LEGAL_ENTITY_NAME_REQUIRED"
            );
        }
        // EaseBuzz compliance: a valid FSSAI license is mandatory for food merchants.
        if (sm.getFssaiNumber() == null || sm.getFssaiNumber().isBlank()) {
            throw new BusinessRuleException(
                "A valid FSSAI license number is required before onboarding to EaseBuzz.",
                "FSSAI_REQUIRED"
            );
        }
        // EaseBuzz CPV: KYC submissions with incomplete core fields produce negative
        // reports. Gate all always-mandatory fields here so a partial draft can never
        // reach EaseBuzz. GST is intentionally NOT gated: it is only sent when present
        // (non-GST merchants are valid), pending confirmation of the per-entity-type
        // mandatory set from EaseBuzz.
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (isBlank(sm.getPan())) missing.add("PAN");
        if (isBlank(sm.getBusinessAddress())) missing.add("business address");
        if (isBlank(sm.getState())) missing.add("state");
        if (isBlank(sm.getBankAccountNo())) missing.add("bank account number");
        if (isBlank(sm.getIfsc())) missing.add("IFSC");
        if (isBlank(sm.getBeneficiaryName())) missing.add("beneficiary name");
        if (isBlank(sm.getContactEmail())) missing.add("contact email");
        if (isBlank(sm.getContactPhone())) missing.add("contact phone");
        if (!missing.isEmpty()) {
            throw new BusinessRuleException(
                "Cannot onboard to EaseBuzz: missing required fields — " + String.join(", ", missing) + ".",
                "MANDATORY_FIELDS_MISSING"
            );
        }
        if (requireBusinessProofs && isProprietorship(sm.getBusinessType())) {
            boolean proof1 = (sm.getBusinessProof1Url() != null && !sm.getBusinessProof1Url().isBlank())
                    || (sm.getBusinessProof1Key() != null && !sm.getBusinessProof1Key().isBlank());
            boolean proof2 = (sm.getBusinessProof2Url() != null && !sm.getBusinessProof2Url().isBlank())
                    || (sm.getBusinessProof2Key() != null && !sm.getBusinessProof2Key().isBlank());
            if (!proof1 && !proof2) {
                throw new BusinessRuleException(
                    "Proprietorship entities require at least one valid business/address proof document for CPV.",
                    "BUSINESS_PROOFS_REQUIRED"
                );
            }
            String type1 = sm.getBusinessProof1Type();
            String type2 = sm.getBusinessProof2Type();
            if (proof1 && isBlank(type1)) {
                throw new BusinessRuleException(
                    "Business proof 1 document type must be specified for CPV.",
                    "BUSINESS_PROOF_TYPES_REQUIRED"
                );
            }
            if (proof2 && isBlank(type2)) {
                throw new BusinessRuleException(
                    "Business proof 2 document type must be specified for CPV.",
                    "BUSINESS_PROOF_TYPES_REQUIRED"
                );
            }
            // If both proofs are provided, they must be of DISTINCT document types.
            if (proof1 && proof2 && type1.trim().equalsIgnoreCase(type2.trim())) {
                throw new BusinessRuleException(
                    "The two business proofs must be of different document types for CPV.",
                    "BUSINESS_PROOF_TYPES_NOT_DISTINCT"
                );
            }
        }
        String subMerchantId = sm.getSubMerchantId();
        if (subMerchantId == null || subMerchantId.isBlank()) {
            Long restId = sm.getRestaurantId() != null ? sm.getRestaurantId() : 0L;
            String restTail = String.format("%06d", restId % 1000000);
            String uuidTail = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
            subMerchantId = "KBSM" + restTail + uuidTail;
            sm.setSubMerchantId(subMerchantId);
        }

        Map<String, Object> result = easebuzzApi.createSubMerchant(
            subMerchantId,
            sm.getBusinessName(), sm.getContactEmail(), sm.getContactPhone(),
            sm.getBankAccountNo(), sm.getIfsc(), sm.getBankName(),
            sm.getBeneficiaryName(), sm.getBranchName(),
            sm.getBusinessType(), sm.getPan(), sm.getGst(),
            sm.getBusinessAddress(),
            sm.getLegalEntityName(), sm.getState(), sm.getFssaiNumber(),
            sm.getUpiDeductionLtLimit(), sm.getDcDeductionGtTwoThousand()
        );
        Object statusObj = result != null ? result.get("status") : null;
        boolean apiStatus = EasebuzzApiClient.toBool(statusObj);
        if (apiStatus && result != null) {
            Object returnedSmIdObj = result.get("submerchant_id");
            if (returnedSmIdObj != null && !returnedSmIdObj.toString().isBlank()) {
                sm.setSubMerchantId(returnedSmIdObj.toString());
            }
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
