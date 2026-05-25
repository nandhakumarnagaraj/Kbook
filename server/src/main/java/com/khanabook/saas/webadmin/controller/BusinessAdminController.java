package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.webadmin.dto.BusinessDashboardResponse;
import com.khanabook.saas.webadmin.dto.BusinessMarketplaceSetupResponse;
import com.khanabook.saas.webadmin.dto.BusinessMenuListItemResponse;
import com.khanabook.saas.webadmin.dto.BusinessOrderListItemResponse;
import com.khanabook.saas.webadmin.dto.BusinessProfileResponse;
import com.khanabook.saas.webadmin.dto.BusinessStaffListItemResponse;
import com.khanabook.saas.webadmin.dto.UpdateBusinessProfileRequest;

import java.math.BigDecimal;
import com.khanabook.saas.webadmin.service.BusinessReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/business")
@RequiredArgsConstructor
public class BusinessAdminController {

    private final BusinessReadService businessReadService;
    private final RestaurantProfileRepository restaurantProfileRepository;

    @GetMapping("/dashboard")
    public ResponseEntity<BusinessDashboardResponse> getDashboard() {
        return ResponseEntity.ok(businessReadService.getDashboard(requireTenant()));
    }

    @GetMapping("/marketplace-setup")
    public ResponseEntity<BusinessMarketplaceSetupResponse> getMarketplaceSetup() {
        return ResponseEntity.ok(businessReadService.getMarketplaceSetup(requireTenant()));
    }

    @GetMapping("/orders")
    public ResponseEntity<List<BusinessOrderListItemResponse>> getOrders() {
        return ResponseEntity.ok(businessReadService.getOrders(requireTenant()));
    }

    @GetMapping("/menu")
    public ResponseEntity<List<BusinessMenuListItemResponse>> getMenu() {
        return ResponseEntity.ok(businessReadService.getMenu(requireTenant()));
    }

    @GetMapping("/staff")
    public ResponseEntity<List<BusinessStaffListItemResponse>> getStaff() {
        return ResponseEntity.ok(businessReadService.getStaff(requireTenant()));
    }

    @PostMapping("/bills/{billId}/manual-refund")
    public ResponseEntity<BusinessOrderListItemResponse> manualRefundBill(
            @PathVariable Long billId,
            @RequestBody com.khanabook.saas.webadmin.dto.RefundBillRequest request) {
        businessReadService.markManualRefund(requireTenant(), billId, request.refundAmount(), request.reason());
        return ResponseEntity.ok(businessReadService.getPosOrder(requireTenant(), billId));
    }

    @GetMapping("/profile")
    public ResponseEntity<BusinessProfileResponse> getProfile() {
        Long tenantId = requireTenant();
        RestaurantProfile p = restaurantProfileRepository.findByRestaurantId(tenantId)
                .filter(existing -> !Boolean.TRUE.equals(existing.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Business not found"));
        return ResponseEntity.ok(toProfileResponse(p));
    }

    @PutMapping("/profile")
    public ResponseEntity<BusinessProfileResponse> updateProfile(@RequestBody UpdateBusinessProfileRequest req) {
        Long tenantId = requireTenant();
        RestaurantProfile p = restaurantProfileRepository.findByRestaurantId(tenantId)
                .filter(existing -> !Boolean.TRUE.equals(existing.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Business not found"));

        if (req.shopName() != null) p.setShopName(req.shopName());
        if (req.shopAddress() != null) p.setShopAddress(req.shopAddress());
        if (req.whatsappNumber() != null) p.setWhatsappNumber(req.whatsappNumber());
        if (req.email() != null) p.setEmail(req.email());
        if (req.currency() != null) p.setCurrency(req.currency());
        if (req.upiEnabled() != null) p.setUpiEnabled(req.upiEnabled());
        if (req.upiHandle() != null) p.setUpiHandle(req.upiHandle());
        if (req.upiMobile() != null) p.setUpiMobile(req.upiMobile());
        if (req.cashEnabled() != null) p.setCashEnabled(req.cashEnabled());
        if (req.posEnabled() != null) p.setPosEnabled(req.posEnabled());
        if (req.zomatoEnabled() != null) p.setZomatoEnabled(req.zomatoEnabled());
        if (req.swiggyEnabled() != null) p.setSwiggyEnabled(req.swiggyEnabled());
        if (req.ownWebsiteEnabled() != null) p.setOwnWebsiteEnabled(req.ownWebsiteEnabled());
        if (req.country() != null) p.setCountry(req.country());
        if (req.timezone() != null) p.setTimezone(req.timezone());
        if (req.gstEnabled() != null) p.setGstEnabled(req.gstEnabled());
        if (req.isTaxInclusive() != null) p.setIsTaxInclusive(req.isTaxInclusive());
        if (req.gstPercentage() != null) p.setGstPercentage(req.gstPercentage());
        if (req.customTaxName() != null) p.setCustomTaxName(req.customTaxName());
        if (req.customTaxNumber() != null) p.setCustomTaxNumber(req.customTaxNumber());
        if (req.customTaxPercentage() != null) p.setCustomTaxPercentage(req.customTaxPercentage());
        if (req.fssaiNumber() != null) {
            p.setFssaiNumber(req.fssaiNumber());
            if (req.fssaiNumber().isBlank()) {
                p.setFssaiExpiryDate(null);
            }
        }
        if (req.fssaiExpiryDate() != null) p.setFssaiExpiryDate(req.fssaiExpiryDate());

        if (req.gstin() != null) {
            p.setGstin(req.gstin());
            if (req.gstin().isBlank()) {
                p.setGstExpiryDate(null);
            }
        }
        if (req.gstExpiryDate() != null) p.setGstExpiryDate(req.gstExpiryDate());

        if (req.reviewUrl() != null) p.setReviewUrl(req.reviewUrl());
        if (req.invoiceFooter() != null) p.setInvoiceFooter(req.invoiceFooter());
        if (req.showBranding() != null) p.setShowBranding(req.showBranding());
        if (req.maskCustomerPhone() != null) p.setMaskCustomerPhone(req.maskCustomerPhone());
        if (req.easebuzzEnabled() != null) p.setEasebuzzEnabled(req.easebuzzEnabled());

        p.setUpdatedAt(System.currentTimeMillis());
        p.setServerUpdatedAt(System.currentTimeMillis());
        p.setDeviceId("server");
        restaurantProfileRepository.save(p);
        return ResponseEntity.ok(toProfileResponse(p));
    }

    private static BusinessProfileResponse toProfileResponse(RestaurantProfile p) {
        return new BusinessProfileResponse(
                p.getRestaurantId(),
                p.getShopName(),
                p.getShopAddress(),
                p.getWhatsappNumber(),
                p.getEmail(),
                p.getLogoUrl(),
                p.getLogoVersion(),
                p.getCurrency(),
                p.getUpiEnabled(),
                p.getUpiHandle(),
                p.getUpiMobile(),
                p.getCashEnabled(),
                p.getPosEnabled(),
                p.getZomatoEnabled(),
                p.getSwiggyEnabled(),
                p.getOwnWebsiteEnabled(),
                p.getCountry(),
                p.getTimezone(),
                p.getGstEnabled(),
                p.getGstin(),
                p.getIsTaxInclusive(),
                p.getGstPercentage(),
                p.getCustomTaxName(),
                p.getCustomTaxNumber(),
                p.getCustomTaxPercentage(),
                p.getFssaiNumber(),
                p.getFssaiExpiryDate(),
                p.getGstExpiryDate(),
                p.getReviewUrl(),
                p.getInvoiceFooter(),
                p.getShowBranding(),
                p.getMaskCustomerPhone(),
                p.getEasebuzzEnabled()
        );
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant context is missing");
        }
        return tenantId;
    }
}
