package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.EasebuzzSubMerchant;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.exception.BusinessRuleException;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.EasebuzzSubMerchantRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final EasebuzzSubMerchantRepository subMerchantRepository;
    private final RestaurantProfileRepository profileRepository;
    private final BillRepository billRepository;
    private final EmailNotificationService emailNotificationService;

    public Map<String, Object> getOnboardingProgress(Long restaurantId) {
        RestaurantProfile profile = profileRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new BusinessRuleException("Business not found"));

        EasebuzzSubMerchant sm = subMerchantRepository.findByRestaurantId(restaurantId).orElse(null);

        boolean profileComplete = isNotBlank(profile.getShopName()) && isNotBlank(profile.getWhatsappNumber());
        boolean businessDetails = sm != null && isNotBlank(sm.getBusinessName()) && isNotBlank(sm.getBankAccountNo());
        boolean kycSubmitted = sm != null && "PENDING_KYC".equals(sm.getStatus()) || "ACTIVE".equals(sm.getStatus()) || "KYC_SUBMITTED".equals(sm.getStatus());
        boolean kycApproved = sm != null && "ACTIVE".equals(sm.getStatus()) && "ACTIVE".equals(sm.getKycStatus());
        boolean splitConfigured = sm != null && sm.getSplitLabel() != null && !sm.getSplitLabel().isBlank();
        boolean easebuzzRegistered = sm != null && sm.getSubMerchantId() != null && !sm.getSubMerchantId().isBlank();

        List<Bill> bills = billRepository.findByRestaurantIdAndIsDeletedFalse(restaurantId);
        long totalOrders = bills.size();
        BigDecimal totalRevenue = bills.stream()
                .filter(b -> "paid".equalsIgnoreCase(b.getPaymentStatus()) || "success".equalsIgnoreCase(b.getPaymentStatus()))
                .map(Bill::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("restaurantId", restaurantId);
        progress.put("shopName", profile.getShopName());
        progress.put("steps", List.of(
            step("profile_complete", "Business Profile", profileComplete),
            step("easebuzz_registered", "Easebuzz Registration", easebuzzRegistered),
            step("business_details", "Bank & Business Details", businessDetails),
            step("kyc_submitted", "KYC Submitted", kycSubmitted),
            step("kyc_approved", "KYC Approved", kycApproved),
            step("split_configured", "Split Label Configured", splitConfigured)
        ));
        progress.put("totalSteps", 6);
        progress.put("completedSteps", countCompleted(progress));
        progress.put("isLive", kycApproved && splitConfigured);
        progress.put("totalOrders", totalOrders);
        progress.put("totalRevenue", totalRevenue);
        return progress;
    }

    private long countCompleted(Map<String, Object> progress) {
        @SuppressWarnings("unchecked")
        var steps = (java.util.List<Map<String, Object>>) progress.get("steps");
        return steps.stream().filter(s -> "complete".equals(s.get("status"))).count();
    }

    @Transactional
    public Map<String, Object> prefillFromProfile(Long restaurantId) {
        RestaurantProfile profile = profileRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new BusinessRuleException("Business not found"));
        EasebuzzSubMerchant sm = subMerchantRepository.findByRestaurantId(restaurantId).orElse(null);
        if (sm == null) {
            sm = new EasebuzzSubMerchant();
            sm.setRestaurantId(restaurantId);
            sm.setStatus("DRAFT");
            long now = System.currentTimeMillis();
            sm.setCreatedAt(now);
            sm.setUpdatedAt(now);
        }

        if (isNotBlank(profile.getShopName()) && isBlank(sm.getBusinessName())) {
            sm.setBusinessName(profile.getShopName());
        }
        if (isNotBlank(profile.getShopAddress()) && isBlank(sm.getBusinessAddress())) {
            sm.setBusinessAddress(profile.getShopAddress());
        }
        if (isNotBlank(profile.getGstin()) && isBlank(sm.getGst())) {
            sm.setGst(profile.getGstin());
        }
        // EaseBuzz compliance: carry FSSAI license into the sub-merchant draft so it
        // can be transmitted at onboarding (mandatory for food merchants).
        if (isNotBlank(profile.getFssaiNumber()) && isBlank(sm.getFssaiNumber())) {
            sm.setFssaiNumber(profile.getFssaiNumber());
        }
        if (profile.getFssaiExpiryDate() != null && sm.getFssaiExpiryDate() == null) {
            sm.setFssaiExpiryDate(profile.getFssaiExpiryDate()
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli());
        }
        if (isNotBlank(profile.getWhatsappNumber()) && isBlank(sm.getContactPhone())) {
            sm.setContactPhone(profile.getWhatsappNumber());
        }
        if (isNotBlank(profile.getEmail()) && isBlank(sm.getContactEmail())) {
            sm.setContactEmail(profile.getEmail());
        }
        sm.setUpdatedAt(System.currentTimeMillis());
        EasebuzzSubMerchant saved = subMerchantRepository.save(sm);

        if (profile.getEmail() != null && !profile.getEmail().isBlank() && profile.getShopName() != null) {
            emailNotificationService.sendOnboardingWelcome(
                profile.getEmail(), profile.getShopName(), profile.getShopName());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "prefilled");
        result.put("subMerchantId", saved.getId());
        result.put("fieldsPrefilled", Map.of(
            "businessName", isNotBlank(saved.getBusinessName()),
            "businessAddress", isNotBlank(saved.getBusinessAddress()),
            "gst", isNotBlank(saved.getGst()),
            "fssaiNumber", isNotBlank(saved.getFssaiNumber()),
            "contactPhone", isNotBlank(saved.getContactPhone()),
            "contactEmail", isNotBlank(saved.getContactEmail())
        ));
        return result;
    }

    private Map<String, String> step(String key, String label, boolean completed) {
        return Map.of("key", key, "label", label, "status", completed ? "complete" : "pending");
    }

    private boolean isNotBlank(String s) { return s != null && !s.isBlank(); }
    private boolean isBlank(String s) { return s == null || s.isBlank(); }
}