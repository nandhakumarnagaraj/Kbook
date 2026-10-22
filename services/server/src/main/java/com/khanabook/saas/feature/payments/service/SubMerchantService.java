package com.khanabook.saas.feature.payments.service;

import com.khanabook.saas.feature.notifications.service.PushNotificationService;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchant;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchantWebhookEvent;
import com.khanabook.saas.feature.payments.data.EasebuzzPayout;
import com.khanabook.saas.core.exception.BusinessRuleException;
import com.khanabook.saas.core.exception.EntityNotFoundException;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchantRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchantWebhookEventRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzPayoutRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final PushNotificationService pushNotificationService;
    private final ObjectMapper objectMapper;

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
            sm.setRestaurantFormat(str(data.get("restaurantFormat")));
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
            sm.setCommissionRate(requireZeroCommission(data.get("commissionRate")));
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

    /** Rent/lease agreements are listed by Easebuzz for individuals/freelancers, not proprietors. */
    private static boolean isRentAgreement(String proofType) {
        if (proofType == null) return false;
        String t = proofType.trim().toUpperCase().replace(" ", "_");
        return t.equals("RENT_AGREEMENT") || t.equals("RENT_AGREEMENT_OR_LEASE")
            || t.equals("LEASE_AGREEMENT");
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
        log.info("Sub-merchant {} assigned Easebuzz ID: {} for restaurant {}; KYC activation remains pending", id, subMerchantId, sm.getRestaurantId());
        
        try {
            pushNotificationService.pushToRestaurant(
                sm.getRestaurantId(),
                "Sub-Merchant Linked",
                "Easebuzz sub-merchant account linked successfully. KYC verification is pending.",
                "kyc",
                String.valueOf(sm.getId()),
                "submerchant",
                null
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
                    null
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
        if (data.containsKey("restaurantFormat")) sm.setRestaurantFormat(data.get("restaurantFormat"));
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
        sm.setCommissionRate(requireZeroCommission(data.get("commissionRate")));
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
     * Legacy sentinel in the {@code *_key} columns, written by the removed API-upload path to mean
     * "delivered to Easebuzz, not stored here". Nothing writes it any more — Easebuzz has no
     * document-ingest API — but rows created before 2026-10-18 still carry it, so the download
     * endpoints must keep treating it as "no local file".
     */
    public static final String EASEBUZZ_HOSTED_MARKER = "EASEBUZZ_HOSTED";

    private static final long KYC_FILE_MAX_BYTES = 10L * 1024 * 1024;
    private static final long KYC_FILE_MIN_BYTES = 64L;

    /**
     * Validates a KYC document the merchant chose and returns the hosted portal where it must be
     * uploaded.
     * <p>
     * Easebuzz has <strong>no document-ingest API</strong>: {@code POST
     * /submerchant/v1/upload_kyc_documents} answered 404 on both {@code testdashboard} and
     * {@code dashboard} (probed 2026-10-18), byte-identical to a route that does not exist, while
     * a documented route answered with application JSON. The file is therefore validated and
     * discarded — never forwarded, never stored, never marked as delivered. Callers must use the
     * returned {@code kycUrl}; the document slot stays empty on purpose, which is what the
     * download endpoints rely on.
     * <p>
     * {@code proofType} is still accepted for wire compatibility with the app, but is no longer
     * recorded: storing a proof type without a proof would tell the status endpoint a document
     * exists when none does.
     * <p>
     * Kept for app builds that still ship the bytes; new builds use
     * {@link #requestKycPortalLink(Long, String)} and send no file at all.
     */
    @Transactional
    public Map<String, Object> submitKycDocument(Long restaurantId, String docType, String proofType, MultipartFile file) {
        EasebuzzSubMerchant sm = registeredSubMerchant(restaurantId);
        String documentType = requireDocumentType(docType);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        if (file.getSize() < KYC_FILE_MIN_BYTES) {
            throw new IllegalArgumentException("Uploaded file is too small to be a valid document");
        }
        if (file.getSize() > KYC_FILE_MAX_BYTES) {
            throw new IllegalArgumentException("File too large; max " + (KYC_FILE_MAX_BYTES / (1024 * 1024)) + " MB");
        }
        return deferToPortal(sm, documentType, file.getSize() + " bytes received and discarded");
    }

    /**
     * Records that the merchant has this document and hands back the portal link to upload it.
     * <p>
     * This is what the app calls. Sending the bytes to an endpoint that is forbidden to keep
     * them cost an upload on a POS terminal, a heap-sized byte array on low-end hardware, and
     * left the merchant believing the document had been submitted. The document type is the
     * only thing this server can act on.
     */
    @Transactional
    public Map<String, Object> requestKycPortalLink(Long restaurantId, String docType) {
        EasebuzzSubMerchant sm = registeredSubMerchant(restaurantId);
        return deferToPortal(sm, requireDocumentType(docType), "no bytes sent");
    }

    private EasebuzzSubMerchant registeredSubMerchant(Long restaurantId) {
        EasebuzzSubMerchant sm = subMerchantRepo.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("EasebuzzSubMerchant", "restaurantId=" + restaurantId));
        if (sm.getSubMerchantId() == null || sm.getSubMerchantId().isBlank()) {
            throw new BusinessRuleException("Restaurant is not registered with Easebuzz yet. Complete registration first.",
                    "sub_merchant_not_registered");
        }
        return sm;
    }

    private String requireDocumentType(String docType) {
        String documentType = easebuzzDocumentType(docType);
        if (documentType == null) {
            throw new IllegalArgumentException("Unknown KYC document type: " + docType);
        }
        return documentType;
    }

    private Map<String, Object> deferToPortal(EasebuzzSubMerchant sm, String documentType, String detail) {
        log.info("KYC document deferred to portal restaurantId={} easebuzzType={} subMerchant={} ({}) — Easebuzz has no document API",
                sm.getRestaurantId(), documentType, sm.getSubMerchantId(), detail);
        sm.setEasebuzzResponse("KYC_UPLOAD_DEFERRED_TO_PORTAL:" + documentType);
        String portalUrl = sm.getKycPortalUrl();
        if (portalUrl == null || portalUrl.isBlank()) {
            // The create response may already have handed us a link. When it did not, mint one
            // here: returning an empty URL leaves the merchant on the one screen that exists to
            // unblock them with nothing to tap. Best-effort — the document note is still recorded
            // when Easebuzz cannot issue a link right now.
            String issued = refreshKycPortalUrl(sm);
            if (issued != null && !issued.isBlank()) {
                sm.setKycPortalUrl(issued);
                portalUrl = issued;
            }
        }
        sm.setUpdatedAt(System.currentTimeMillis());
        subMerchantRepo.save(sm);
        return Map.of(
                "status", "portal_required",
                "subMerchantId", sm.getSubMerchantId(),
                "documentType", documentType,
                "kycUrl", portalUrl != null ? portalUrl : "",
                "message", "Upload this document on the Easebuzz portal to finish verification.");
    }

    /**
     * Issues a fresh hosted-KYC link for a sub-merchant, returning {@code ""} when Easebuzz cannot
     * provide one. The link is an access credential, so only its absence or presence is logged.
     */
    private String refreshKycPortalUrl(EasebuzzSubMerchant sm) {
        try {
            Object issued = generateKycAccessKey(sm.getId()).get("kyc_url");
            return issued != null ? issued.toString() : "";
        } catch (RuntimeException e) {
            log.warn("Could not issue a KYC portal link for sub-merchant {}: {}",
                    sm.getSubMerchantId(), e.getMessage());
            return "";
        }
    }

    /**
     * Maps our slot name to the Easebuzz document type. Easebuzz has no document type per proof
     * kind, so both business-proof slots are filed upstream as ADDRESS_PROOF; the finer
     * {@code proofType} the app collects (RENT_AGREEMENT, GST_CERTIFICATE, FSSAI_LICENSE, …) is
     * kept on our own row as metadata only and never sent. Do not add a mapping here without a
     * documented Easebuzz value to map onto.
     */
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

    /**
     * The webhook event's payload column is JSONB, so the stored body must be real JSON.
     * A Map's toString() produces {k=v}, which Postgres rejects - and because this runs
     * inside the webhook transaction, that rejection discards the KYC status update too.
     */
    private String toStoredPayloadJson(Map<String, Object> payload, String eventType, String subMerchantId) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Could not serialize sub-merchant webhook payload (subMerchantId={}); "
                    + "storing a minimal valid JSON record", subMerchantId, e);
            String safeEvent = eventType == null ? "" : eventType.replace("\\", "\\\\").replace("\"", "\\\"");
            return "{\"event\":\"" + safeEvent + "\",\"payload_serialization_error\":true}";
        }
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
        String kycExpiryDate = data.containsKey("kyc_expiry_date") && data.get("kyc_expiry_date") != null
                ? data.get("kyc_expiry_date").toString() : null;

        long now = System.currentTimeMillis();

        Optional<EasebuzzSubMerchant> smOpt = Optional.empty();
        if (subMerchantId != null && !subMerchantId.isBlank()) {
            smOpt = subMerchantRepo.findBySubMerchantId(subMerchantId);
        }
        if (smOpt.isEmpty() && email != null && !email.isBlank()) {
            // contact_email is not unique - Easebuzz allows one owner's outlets to share it -
            // so a single-result finder would throw on a legitimate duplicate. Only attribute
            // the event when the email identifies exactly one sub-merchant.
            java.util.List<EasebuzzSubMerchant> byEmail = subMerchantRepo.findAllByContactEmail(email);
            if (byEmail.size() == 1) {
                smOpt = Optional.of(byEmail.get(0));
            } else if (byEmail.size() > 1) {
                log.error("KYC webhook cannot be attributed: {} sub-merchants share contact email {} "
                        + "(payload id={}). Reconciliation must match on submerchant_id.",
                        byEmail.size(), email, subMerchantId);
                return;
            }
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
        event.setPayload(toStoredPayloadJson(payload, eventType, subMerchantId));
        event.setReceivedAt(now);
        event.setProcessed(false);
        webhookEventRepo.save(event);

        if (kycUrl != null && !kycUrl.isBlank()) {
            sm.setKycPortalUrl(kycUrl);
        }
        if (kycExpiryDate != null && !kycExpiryDate.isBlank()) {
            sm.setKycExpiryDate(kycExpiryDate);
        }

        // Easebuzz reports bank verification separately from KYC approval, and either can be
        // false on its own. A sub-merchant cannot accept payments until both are true, so an
        // approved KYC alone must not flip the row to ACTIVE.
        boolean bankStatusSupplied = data.get("bank_status") != null;
        boolean bankVerified = isTruthy(data.get("bank_status"));

        if (kycProfileStatus != null || kycStatus != null) {
            if (bankStatusSupplied) {
                sm.setBankStatus(bankVerified);
            }
            boolean kycApproved = "Completed".equalsIgnoreCase(kycProfileStatus)
                    || "True".equalsIgnoreCase(kycStatus) || Boolean.TRUE.equals(kycStatusObj);
            if (kycApproved) {
                sm.setKycStatus("True");
                if (bankStatusSupplied && !bankVerified) {
                    log.warn("Sub-merchant {} KYC approved but bank_status=false - not activating; "
                            + "payments cannot be accepted until the bank account is verified",
                            sm.getSubMerchantId());
                } else {
                    sm.setStatus("ACTIVE");
                    sm.setKycActivatedAt(now);
                }
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
                null
            );
            } catch (Exception e) {
                log.warn("Failed to push KYC status update notification: {}", e.getMessage());
            }
        }
        event.setProcessed(true);
        webhookEventRepo.save(event);
        log.info("Processed sub-merchant KYC webhook subMerchantId={} kycStatus={}", subMerchantId, kycStatus);
    }

    /**
     * Manual unblock for a merchant whose bank is verified but whose row never reached ACTIVE.
     * Easebuzz sends no webhook when bank_status changes after KYC approval, so without a
     * manual path an approved-but-unverified merchant would be stranded indefinitely. Call
     * only after Easebuzz support confirms the bank account.
     */
    @Transactional
    public EasebuzzSubMerchant markBankVerified(Long id) {
        EasebuzzSubMerchant sm = getById(id);
        String previous = sm.getStatus();
        sm.setBankStatus(true);
        sm.setUpdatedAt(System.currentTimeMillis());
        if ("True".equalsIgnoreCase(sm.getKycStatus()) && !"ACTIVE".equals(sm.getStatus())) {
            sm.setStatus("ACTIVE");
            if (sm.getKycActivatedAt() == null) {
                sm.setKycActivatedAt(System.currentTimeMillis());
            }
        }
        EasebuzzSubMerchant saved = subMerchantRepo.save(sm);
        log.warn("SECURITY_AUDIT: bank marked verified manually for sub-merchant {} (status {} -> {})",
                sm.getSubMerchantId(), previous, saved.getStatus());
        return saved;
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
            sm.setEasebuzzResponse(toStorageSafeJson(result));
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
        // A create response may already carry the hosted KYC link, so this call is a refresh,
        // not the only source. Never blank a working link on a failed refresh: that would strand
        // a merchant who already had a usable portal URL.
        String kycUrl = extractKycPortalUrl(result);
        if (kycUrl != null) {
            sm.setKycPortalUrl(kycUrl);
            sm.setUpdatedAt(System.currentTimeMillis());
            subMerchantRepo.save(sm);
        } else if (sm.getKycPortalUrl() != null) {
            kycUrl = sm.getKycPortalUrl();
        } else {
            kycUrl = "";
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
        
        Long restaurantId = com.khanabook.saas.core.security.TenantContext.getCurrentTenant();
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
        // reach EaseBuzz. GST is intentionally NOT gated: Easebuzz confirmed to the merchant
        // (2026-10-17) that a GST certificate is not required for sub-merchant KYC, so it is
        // only sent when present. PAN, FSSAI, bank details, one business proof and Aadhaar
        // remain the KYC set (the last two are collected on Easebuzz's hosted portal).
        java.util.List<String> missing = new java.util.ArrayList<>();
        // The outlet format is deliberately NOT gated: Easebuzz assigns the MCC internally and
        // does not document a create-time MCC field (ERA, 2026-10-18), so requiring a value that
        // may have no effect would only block merchants at submit. It is still stored when
        // supplied, as ops data and as the only record of what we intended.
        if (isBlank(sm.getPan())) missing.add("PAN");
        if (isBlank(sm.getBusinessAddress())) missing.add("business address");
        if (isBlank(sm.getState())) missing.add("state");
        if (isBlank(sm.getBankAccountNo())) missing.add("bank account number");
        if (isBlank(sm.getIfsc())) missing.add("IFSC");
        if (isBlank(sm.getBankName())) missing.add("bank name");
        if (isBlank(sm.getBranchName())) missing.add("branch name");
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
            // Easebuzz accepts Shop & Establishment, Import/Export certificate, trade
            // licence, Udyam (URC) or a utility bill as a proprietorship's business
            // proof. A rent/lease agreement is only listed for individuals/freelancers,
            // so it cannot be the merchant's only proof - that reaches CPV and comes
            // back as a negative report.
            boolean hasNonRentProof = (proof1 && !isRentAgreement(type1))
                    || (proof2 && !isRentAgreement(type2));
            if (!hasNonRentProof) {
                throw new BusinessRuleException(
                    "A rent/lease agreement cannot be the only business proof for a proprietorship. "
                        + "Submit at least one of: Shop & Establishment certificate, Import/Export certificate, "
                        + "trade licence, Udyam (URC) certificate, or a utility bill.",
                    "BUSINESS_PROOF_TYPE_NOT_ACCEPTED"
                );
            }
        }
        // Easebuzz owns this identifier: the generate_kyc_access_key spec describes
        // sub_merchant_id as the "Unique ID for Submerchant created by EaseBuzz". Send one back
        // only when the gateway already issued it (resubmit); a locally invented id would be
        // echoed upstream as if it were theirs and then used for KYC calls that only accept
        // the gateway-issued value.
        String subMerchantId = sm.getSubMerchantId();

        Map<String, Object> result = easebuzzApi.createSubMerchant(
            subMerchantId,
            sm.getBusinessName(), sm.getContactEmail(), sm.getContactPhone(),
            sm.getBankAccountNo(), sm.getIfsc(), sm.getBankName(),
            sm.getBeneficiaryName(), sm.getBranchName(),
            sm.getBusinessType(), sm.getPan(), sm.getGst(),
            sm.getBusinessAddress(),
            sm.getLegalEntityName(), sm.getState(), sm.getFssaiNumber(),
            sm.getRestaurantFormat(),
            sm.getUpiDeductionLtLimit(), sm.getDcDeductionGtTwoThousand()
        );
        Object statusObj = result != null ? result.get("status") : null;
        boolean apiStatus = EasebuzzApiClient.toBool(statusObj);
        if (apiStatus && result != null) {
            String returnedSmId = extractSubMerchantId(result);
            if (returnedSmId != null) {
                sm.setSubMerchantId(returnedSmId);
            } else {
                log.warn("Easebuzz accepted sub-merchant {} but returned no sub-merchant id; "
                        + "KYC portal and payments stay blocked until one arrives", id);
            }
            // The create response can already carry the hosted KYC link. Persisting it lets the
            // owner open the portal straight after onboarding, without a separate
            // generate_kyc_access_key round trip; that call stays available as a refresh.
            String createKycUrl = extractKycPortalUrl(result);
            if (createKycUrl != null) {
                sm.setKycPortalUrl(createKycUrl);
                log.info("Sub-merchant {} create response carried a KYC portal link; "
                        + "the owner can onboard documents without a separate access-key call", id);
            }
            // The create response often omits kyc_details, and the portal is the only place a KYC
            // document can go (no document-ingest API). Issuing the link here means the owner sees
            // a working "upload documents" action the moment onboarding succeeds, instead of
            // having to discover a separate refresh step. Best-effort: registration already
            // succeeded, so a failed access-key call must not turn it into a failure.
            if (createKycUrl == null && sm.getSubMerchantId() != null && !sm.getSubMerchantId().isBlank()) {
                try {
                    Map<String, Object> keyResult = easebuzzApi.generateKycAccessKey(
                            sm.getSubMerchantId(), sm.getBusinessName(),
                            sm.getContactEmail(), sm.getContactPhone());
                    String issuedUrl = extractKycPortalUrl(keyResult);
                    if (issuedUrl != null) {
                        sm.setKycPortalUrl(issuedUrl);
                        log.info("Sub-merchant {} KYC portal link issued immediately after create", id);
                    } else {
                        log.warn("Sub-merchant {} registered but no KYC portal link was issued; "
                                + "the owner must refresh the link before uploading documents", id);
                    }
                } catch (Exception e) {
                    log.warn("Sub-merchant {} registered; KYC portal link request failed: {}", id, e.getMessage());
                }
            }
            sm.setStatus("PENDING_KYC");
            sm.setKycSubmittedAt(System.currentTimeMillis());
            sm.setEasebuzzResponse(toStorageSafeJson(result));
        } else {
            sm.setStatus("FAILED");
            String error = extractProviderError(result);
            log.error("Easebuzz submission failed for smId={}: {}", id, error);
            sm.setEasebuzzResponse(error);
        }
        sm.setUpdatedAt(System.currentTimeMillis());
        subMerchantRepo.save(sm);
        log.info("Sub-merchant {} submitted to Easebuzz, status={}", id, sm.getStatus());
        return sm;
    }

    /**
     * Picks the gateway's human-readable failure text out of a create response.
     * <p>
     * Easebuzz returns {@code {"status":false,"error":...,"error_desc":...}}, and either field can
     * be null depending on the failure. Falling back to the whole map dumped operators a raw JSON
     * blob in the onboarding screen, which is exactly the diagnostic that does not help them — so
     * {@code error_desc} wins, then {@code error}/{@code msg}/{@code message}, then the raw map.
     */
    public static String extractProviderError(Map<String, Object> result) {
        if (result == null) return "Unknown error";
        for (String key : new String[]{"error_desc", "error", "msg", "message"}) {
            Object value = result.get(key);
            if (value == null || value instanceof Boolean || value instanceof Number) continue;
            String text = value.toString().trim();
            if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) return text;
        }
        return result.toString();
    }

    /**
     * Reads the gateway-issued sub-merchant id out of a create response.
     * <p>
     * Easebuzz documents it nested — {@code {"status":true,"submerchant":{"submerchant_id":"SXXXABCD"}}}
     * — while top-level placement has also been observed on this project's account. Both shapes
     * are accepted, and {@code msg}/{@code data} envelopes are unwrapped for the same reason.
     * Returns {@code null} when the response carries no id at all, which the caller must treat as
     * "not onboarded yet" rather than substituting one of its own.
     */
    public static String extractSubMerchantId(Map<String, Object> result) {
        if (result == null) return null;
        String direct = asId(result.get("submerchant_id"));
        if (direct != null) return direct;
        for (String container : new String[]{"submerchant", "data", "msg"}) {
            if (result.get(container) instanceof Map<?, ?> nested) {
                String id = asId(nested.get("submerchant_id"));
                if (id != null) return id;
            }
        }
        return null;
    }

    private static String asId(Object value) {
        if (value == null) return null;
        String id = value.toString().trim();
        return id.isEmpty() ? null : id;
    }

    /**
     * Reads the hosted KYC portal URL out of a gateway response.
     * <p>
     * Easebuzz documents it on {@code generate_kyc_access_key} as
     * {@code {"status":true,"kyc_dashboard_url":"https://kyc.easebuzz.in/...?access_key=..."}},
     * and this project's account has also returned the same link inside the create response
     * ({@code kyc_details.kyc_dashboard_url}). Both shapes are accepted so the caller never has to
     * assume a separate access-key call is required; only absolute http(s) URLs qualify, which
     * keeps a plain {@code msg} sentence from being stored as if it were a link.
     *
     * @return the URL, or {@code null} when the response carries none
     */
    public static String extractKycPortalUrl(Map<String, Object> result) {
        if (result == null) return null;
        for (String key : new String[]{"kyc_dashboard_url", "kyc_url"}) {
            String url = asPortalUrl(result.get(key));
            if (url != null) return url;
        }
        for (String container : new String[]{"kyc_details", "submerchant", "data", "msg"}) {
            if (result.get(container) instanceof Map<?, ?> nested) {
                for (String key : new String[]{"kyc_dashboard_url", "kyc_url", "url"}) {
                    String url = asPortalUrl(nested.get(key));
                    if (url != null) return url;
                }
            }
        }
        return asPortalUrl(result.get("msg"));
    }

    private static String asPortalUrl(Object value) {
        if (value == null || value instanceof Map || value instanceof Boolean || value instanceof Number) return null;
        String candidate = value.toString().trim();
        String lower = candidate.toLowerCase(java.util.Locale.ROOT);
        boolean absolute = lower.startsWith("https://") || lower.startsWith("http://");
        return absolute && candidate.contains(".") ? candidate : null;
    }

    /** Response keys whose values are KYC access credentials and must never be persisted or logged. */
    private static final java.util.Set<String> KYC_SECRET_KEYS =
            java.util.Set.of("kyc_dashboard_url", "kyc_url", "access_key", "kyc_access_key");

    /**
     * Serialises a gateway response for storage with every KYC access URL/key removed.
     * <p>
     * {@code easebuzz_response} is surfaced verbatim as the onboarding {@code message} and is read
     * by support, so a raw create response would leak a portal URL that signs anyone holding it
     * into the merchant's KYC session. Redaction is structural (the field survives as
     * {@code [REDACTED]}) so diagnostics stay useful; on any serialisation failure the method fails
     * closed rather than falling back to the raw map.
     */
    private String toStorageSafeJson(Map<String, Object> result) {
        try {
            com.fasterxml.jackson.databind.JsonNode tree = objectMapper.valueToTree(result);
            redactKycSecrets(tree);
            return objectMapper.writeValueAsString(tree);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Could not serialise Easebuzz response for storage; storing a redacted stub");
            return "{\"payload_redacted\":true}";
        }
    }

    private static void redactKycSecrets(com.fasterxml.jackson.databind.JsonNode node) {
        if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode obj) {
            java.util.List<String> names = new java.util.ArrayList<>();
            obj.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                com.fasterxml.jackson.databind.JsonNode child = obj.get(name);
                if (child == null) continue;
                String key = name.toLowerCase(java.util.Locale.ROOT);
                boolean secretKey = KYC_SECRET_KEYS.stream().anyMatch(key::contains);
                if (child.isValueNode() && !child.isNull()) {
                    String text = child.asText();
                    if (secretKey || text.toLowerCase(java.util.Locale.ROOT).contains("access_key=")) {
                        obj.put(name, "[REDACTED]");
                    }
                } else {
                    redactKycSecrets(child);
                }
            }
        } else if (node instanceof com.fasterxml.jackson.databind.node.ArrayNode arr) {
            arr.forEach(SubMerchantService::redactKycSecrets);
        }
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

    private static java.math.BigDecimal requireZeroCommission(Object value) {
        if (value != null && new java.math.BigDecimal(value.toString()).compareTo(java.math.BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("KhanaBook does not charge transaction commission");
        }
        return java.math.BigDecimal.ZERO;
    }
}
