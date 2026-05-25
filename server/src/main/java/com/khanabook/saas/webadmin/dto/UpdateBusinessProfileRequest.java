package com.khanabook.saas.webadmin.dto;

import java.math.BigDecimal;

public record UpdateBusinessProfileRequest(
    String shopName,
    String shopAddress,
    String whatsappNumber,
    String email,
    String currency,
    Boolean upiEnabled,
    String upiHandle,
    String upiMobile,
    Boolean cashEnabled,
    Boolean posEnabled,
    Boolean zomatoEnabled,
    Boolean swiggyEnabled,
    Boolean ownWebsiteEnabled,
    String country,
    String timezone,
    Boolean gstEnabled,
    String gstin,
    Boolean isTaxInclusive,
    BigDecimal gstPercentage,
    String customTaxName,
    String customTaxNumber,
    BigDecimal customTaxPercentage,
    String fssaiNumber,
    String reviewUrl,
    String invoiceFooter,
    Boolean showBranding,
    Boolean maskCustomerPhone,
    Boolean easebuzzEnabled
) {}
