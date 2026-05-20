package com.khanabook.saas.sync.dto.payload;

import com.khanabook.saas.sync.entity.BaseSyncEntity;
import com.khanabook.saas.entity.*;
import org.springframework.beans.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class SyncMapper {
    private static final Logger log = LoggerFactory.getLogger(SyncMapper.class);

    public static <S extends BaseSyncEntity, T> T map(S source, Class<T> targetClass) {
        if (source == null) return null;
        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(source, target);
            
            if (source instanceof Category entity && target instanceof CategoryDTO dto) {
                dto.setId(entity.getId());
                dto.setLocalId(entity.getLocalId());
                dto.setServerUpdatedAt(entity.getServerUpdatedAt());
            } else if (source instanceof MenuItem entity && target instanceof MenuItemDTO dto) {
                dto.setId(entity.getId());
                dto.setLocalId(entity.getLocalId());
                dto.setServerUpdatedAt(entity.getServerUpdatedAt());
                dto.setCategoryId(entity.getCategoryId());
                dto.setServerCategoryId(entity.getServerCategoryId());
            } else if (source instanceof ItemVariant entity && target instanceof ItemVariantDTO dto) {
                dto.setId(entity.getId());
                dto.setLocalId(entity.getLocalId());
                dto.setServerUpdatedAt(entity.getServerUpdatedAt());
                dto.setMenuItemId(entity.getMenuItemId());
                dto.setServerMenuItemId(entity.getServerMenuItemId());
            } else if (source instanceof Bill entity && target instanceof BillDTO dto) {
                dto.setId(entity.getId());
                dto.setLocalId(entity.getLocalId());
                dto.setServerUpdatedAt(entity.getServerUpdatedAt());
            } else if (source instanceof BillItem entity && target instanceof BillItemDTO dto) {
                dto.setId(entity.getId());
                dto.setLocalId(entity.getLocalId());
                dto.setServerUpdatedAt(entity.getServerUpdatedAt());
                dto.setBillId(entity.getBillId());
                dto.setServerBillId(entity.getServerBillId());
                dto.setMenuItemId(entity.getMenuItemId());
                dto.setServerMenuItemId(entity.getServerMenuItemId());
                dto.setVariantId(entity.getVariantId());
                dto.setServerVariantId(entity.getServerVariantId());
            } else if (source instanceof BillPayment entity && target instanceof BillPaymentDTO dto) {
                dto.setId(entity.getId());
                dto.setLocalId(entity.getLocalId());
                dto.setServerUpdatedAt(entity.getServerUpdatedAt());
                dto.setBillId(entity.getBillId());
                dto.setServerBillId(entity.getServerBillId());
            } else if (source instanceof StockLog entity && target instanceof StockLogDTO dto) {
                dto.setId(entity.getId());
                dto.setLocalId(entity.getLocalId());
                dto.setServerUpdatedAt(entity.getServerUpdatedAt());
                dto.setMenuItemId(entity.getMenuItemId());
                dto.setVariantId(entity.getVariantId());
                dto.setServerMenuItemId(entity.getServerMenuItemId());
                dto.setServerVariantId(entity.getServerVariantId());
            } else if (source instanceof RestaurantProfile entity && target instanceof RestaurantProfileDTO dto) {
                dto.setId(entity.getId());
                dto.setLocalId(entity.getLocalId());
                dto.setServerUpdatedAt(entity.getServerUpdatedAt());
                dto.setShopName(entity.getShopName());
                dto.setShopAddress(entity.getShopAddress());
                dto.setWhatsappNumber(entity.getWhatsappNumber());
                dto.setEmail(entity.getEmail());
                dto.setFssaiNumber(entity.getFssaiNumber());
                dto.setLogoPath(entity.getLogoPath());
                dto.setLogoUrl(entity.getLogoUrl());
                dto.setLogoVersion(entity.getLogoVersion());
                dto.setCountry(entity.getCountry());
                dto.setCurrency(entity.getCurrency());
                dto.setTimezone(entity.getTimezone());
                dto.setGstEnabled(entity.getGstEnabled());
                dto.setGstin(entity.getGstin());
                dto.setIsTaxInclusive(entity.getIsTaxInclusive());
                dto.setGstPercentage(entity.getGstPercentage());
                dto.setCustomTaxName(entity.getCustomTaxName());
                dto.setCustomTaxNumber(entity.getCustomTaxNumber());
                dto.setCustomTaxPercentage(entity.getCustomTaxPercentage());
                dto.setUpiEnabled(entity.getUpiEnabled());
                dto.setUpiQrPath(entity.getUpiQrPath());
                dto.setUpiQrUrl(entity.getUpiQrUrl());
                dto.setUpiQrVersion(entity.getUpiQrVersion());
                dto.setUpiHandle(entity.getUpiHandle());
                dto.setUpiMobile(entity.getUpiMobile());
                dto.setCashEnabled(entity.getCashEnabled());
                dto.setPosEnabled(entity.getPosEnabled());
                dto.setPrinterEnabled(entity.getPrinterEnabled());
                dto.setPrinterName(entity.getPrinterName());
                dto.setPrinterMac(entity.getPrinterMac());
                dto.setPaperSize(entity.getPaperSize());
                dto.setAutoPrintOnSuccess(entity.getAutoPrintOnSuccess());
                dto.setIncludeLogoInPrint(entity.getIncludeLogoInPrint());
                dto.setDailyOrderCounter(entity.getDailyOrderCounter());
                dto.setLifetimeOrderCounter(entity.getLifetimeOrderCounter());
                dto.setLastResetDate(entity.getLastResetDate());
                dto.setSessionTimeoutMinutes(entity.getSessionTimeoutMinutes());
                dto.setKitchenPrinterEnabled(entity.getKitchenPrinterEnabled());
                dto.setKitchenPrinterName(entity.getKitchenPrinterName());
                dto.setKitchenPrinterMac(entity.getKitchenPrinterMac());
                dto.setKitchenPrinterPaperSize(entity.getKitchenPrinterPaperSize());
                dto.setInvoiceFooter(entity.getInvoiceFooter());
                dto.setReviewUrl(entity.getReviewUrl());
                dto.setZomatoEnabled(entity.getZomatoEnabled());
                dto.setSwiggyEnabled(entity.getSwiggyEnabled());
                dto.setEasebuzzEnabled(entity.getEasebuzzEnabled());
                dto.setOwnWebsiteEnabled(entity.getOwnWebsiteEnabled());
                dto.setShowBranding(entity.getShowBranding());
                dto.setMaskCustomerPhone(entity.getMaskCustomerPhone());
                dto.setZomatoOutletId(entity.getZomatoOutletId());
                dto.setSwiggyStoreId(entity.getSwiggyStoreId());
                dto.setMarketplaceNotes(entity.getMarketplaceNotes());
                dto.setZomatoApiKey(entity.getZomatoApiKey());
                dto.setZomatoWebhookSecret(entity.getZomatoWebhookSecret());
                dto.setSwiggyApiKey(entity.getSwiggyApiKey());
                dto.setSwiggyWebhookSecret(entity.getSwiggyWebhookSecret());
                dto.setPrintCustomerWhatsapp(entity.getPrintCustomerWhatsapp());
            } else if (source instanceof User entity && target instanceof UserDTO dto) {
                dto.setId(entity.getId());
                dto.setLocalId(entity.getLocalId());
                dto.setServerUpdatedAt(entity.getServerUpdatedAt());
            }

            return target;
        } catch (Exception e) {
            throw new RuntimeException("Failed to map Entity to DTO", e);
        }
    }

    public static <S extends BaseSyncEntity, T> List<T> mapList(List<S> sourceList, Class<T> targetClass) {
        if (sourceList == null) return new ArrayList<>();
        List<T> result = new ArrayList<>();
        for (S source : sourceList) {
            T mapped = map(source, targetClass);
            if (mapped != null) result.add(mapped);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static <S, T extends BaseSyncEntity> T mapToEntity(S source, Class<T> targetClass) {
        if (source == null) return null;
        try {
            T target;
            if (targetClass.equals(Category.class)) target = (T) new Category();
            else if (targetClass.equals(MenuItem.class)) target = (T) new MenuItem();
            else if (targetClass.equals(ItemVariant.class)) target = (T) new ItemVariant();
            else if (targetClass.equals(Bill.class)) target = (T) new Bill();
            else if (targetClass.equals(BillItem.class)) target = (T) new BillItem();
            else if (targetClass.equals(BillPayment.class)) target = (T) new BillPayment();
            else if (targetClass.equals(StockLog.class)) target = (T) new StockLog();
            else if (targetClass.equals(RestaurantProfile.class)) target = (T) new RestaurantProfile();
            else if (targetClass.equals(User.class)) target = (T) new User();
            else target = targetClass.getDeclaredConstructor().newInstance();

            BeanUtils.copyProperties(source, target);

            if (source instanceof CategoryDTO dto) {
                Category entity = (Category) target;
                entity.setId(dto.getId());
                entity.setLocalId(dto.getLocalId());
                entity.setServerUpdatedAt(dto.getServerUpdatedAt());
                if (dto.getIsActive() == null) {
                    entity.setIsActive(true);
                }
            } else if (source instanceof MenuItemDTO dto) {
                MenuItem entity = (MenuItem) target;
                entity.setId(dto.getId());
                entity.setLocalId(dto.getLocalId());
                entity.setServerUpdatedAt(dto.getServerUpdatedAt());
                entity.setCategoryId(dto.getCategoryId());
                entity.setServerCategoryId(dto.getServerCategoryId());
                entity.setOverwriteExisting(dto.getOverwriteExisting());
                if (dto.getIsAvailable() == null) {
                    entity.setIsAvailable(true);
                }
            } else if (source instanceof ItemVariantDTO dto) {
                ItemVariant entity = (ItemVariant) target;
                entity.setId(dto.getId());
                entity.setLocalId(dto.getLocalId());
                entity.setServerUpdatedAt(dto.getServerUpdatedAt());
                entity.setMenuItemId(dto.getMenuItemId());
                entity.setServerMenuItemId(dto.getServerMenuItemId());
                if (dto.getIsAvailable() == null) {
                    entity.setIsAvailable(true);
                }
            } else if (source instanceof BillDTO dto) {
                Bill entity = (Bill) target;
                entity.setId(dto.getId());
                entity.setLocalId(dto.getLocalId());
                entity.setServerUpdatedAt(dto.getServerUpdatedAt());
            } else if (source instanceof BillItemDTO dto) {
                BillItem entity = (BillItem) target;
                entity.setId(dto.getId());
                entity.setLocalId(dto.getLocalId());
                entity.setServerUpdatedAt(dto.getServerUpdatedAt());
                entity.setBillId(dto.getBillId());
                entity.setServerBillId(dto.getServerBillId());
                entity.setMenuItemId(dto.getMenuItemId());
                entity.setServerMenuItemId(dto.getServerMenuItemId());
                entity.setVariantId(dto.getVariantId());
                entity.setServerVariantId(dto.getServerVariantId());
            } else if (source instanceof BillPaymentDTO dto) {
                BillPayment entity = (BillPayment) target;
                entity.setId(dto.getId());
                entity.setLocalId(dto.getLocalId());
                entity.setServerUpdatedAt(dto.getServerUpdatedAt());
                entity.setBillId(dto.getBillId());
                entity.setServerBillId(dto.getServerBillId());
            } else if (source instanceof StockLogDTO dto) {
                StockLog entity = (StockLog) target;
                entity.setId(dto.getId());
                entity.setLocalId(dto.getLocalId());
                entity.setServerUpdatedAt(dto.getServerUpdatedAt());
                entity.setMenuItemId(dto.getMenuItemId());
                entity.setVariantId(dto.getVariantId());
                entity.setServerMenuItemId(dto.getServerMenuItemId());
                entity.setServerVariantId(dto.getServerVariantId());
            } else if (source instanceof RestaurantProfileDTO dto) {
                RestaurantProfile entity = (RestaurantProfile) target;
                entity.setId(dto.getId());
                entity.setLocalId(dto.getLocalId());
                entity.setServerUpdatedAt(dto.getServerUpdatedAt());
                entity.setShopName(dto.getShopName());
                entity.setShopAddress(dto.getShopAddress());
                entity.setWhatsappNumber(dto.getWhatsappNumber());
                entity.setEmail(dto.getEmail());
                entity.setFssaiNumber(dto.getFssaiNumber());
                entity.setLogoPath(dto.getLogoPath());
                entity.setLogoUrl(dto.getLogoUrl());
                entity.setLogoVersion(dto.getLogoVersion());
                entity.setCountry(dto.getCountry());
                entity.setCurrency(dto.getCurrency());
                entity.setTimezone(dto.getTimezone());
                entity.setGstEnabled(dto.getGstEnabled());
                entity.setGstin(dto.getGstin());
                entity.setIsTaxInclusive(dto.getIsTaxInclusive());
                entity.setGstPercentage(dto.getGstPercentage());
                entity.setCustomTaxName(dto.getCustomTaxName());
                entity.setCustomTaxNumber(dto.getCustomTaxNumber());
                entity.setCustomTaxPercentage(dto.getCustomTaxPercentage());
                entity.setUpiEnabled(dto.getUpiEnabled());
                entity.setUpiQrPath(dto.getUpiQrPath());
                entity.setUpiQrUrl(dto.getUpiQrUrl());
                entity.setUpiQrVersion(dto.getUpiQrVersion());
                entity.setUpiHandle(dto.getUpiHandle());
                entity.setUpiMobile(dto.getUpiMobile());
                entity.setCashEnabled(dto.getCashEnabled());
                entity.setPosEnabled(dto.getPosEnabled());
                entity.setPrinterEnabled(dto.getPrinterEnabled());
                entity.setPrinterName(dto.getPrinterName());
                entity.setPrinterMac(dto.getPrinterMac());
                entity.setPaperSize(dto.getPaperSize());
                entity.setAutoPrintOnSuccess(dto.getAutoPrintOnSuccess());
                entity.setIncludeLogoInPrint(dto.getIncludeLogoInPrint());
                entity.setDailyOrderCounter(dto.getDailyOrderCounter());
                entity.setLifetimeOrderCounter(dto.getLifetimeOrderCounter());
                entity.setLastResetDate(dto.getLastResetDate());
                entity.setSessionTimeoutMinutes(dto.getSessionTimeoutMinutes());
                entity.setKitchenPrinterEnabled(dto.getKitchenPrinterEnabled());
                entity.setKitchenPrinterName(dto.getKitchenPrinterName());
                entity.setKitchenPrinterMac(dto.getKitchenPrinterMac());
                entity.setKitchenPrinterPaperSize(dto.getKitchenPrinterPaperSize());
                entity.setInvoiceFooter(dto.getInvoiceFooter());
                entity.setReviewUrl(dto.getReviewUrl());
                entity.setZomatoEnabled(dto.getZomatoEnabled());
                entity.setSwiggyEnabled(dto.getSwiggyEnabled());
                entity.setEasebuzzEnabled(dto.getEasebuzzEnabled());
                entity.setOwnWebsiteEnabled(dto.getOwnWebsiteEnabled());
                entity.setShowBranding(dto.getShowBranding());
                entity.setMaskCustomerPhone(dto.getMaskCustomerPhone());
                entity.setZomatoOutletId(dto.getZomatoOutletId());
                entity.setSwiggyStoreId(dto.getSwiggyStoreId());
                entity.setMarketplaceNotes(dto.getMarketplaceNotes());
                entity.setZomatoApiKey(dto.getZomatoApiKey());
                entity.setZomatoWebhookSecret(dto.getZomatoWebhookSecret());
                entity.setSwiggyApiKey(dto.getSwiggyApiKey());
                entity.setSwiggyWebhookSecret(dto.getSwiggyWebhookSecret());
                entity.setPrintCustomerWhatsapp(dto.getPrintCustomerWhatsapp());
                if (dto.getLastResetDate() != null && !dto.getLastResetDate().isEmpty()) {
                    entity.setLastResetDate(dto.getLastResetDate());
                    try {
                        entity.setLastResetDateProper(java.time.LocalDate.parse(dto.getLastResetDate()));
                    } catch (Exception ignored) {
                    }
                }
            } else if (source instanceof UserDTO dto) {
                User entity = (User) target;
                entity.setId(dto.getId());
                entity.setLocalId(dto.getLocalId());
                entity.setServerUpdatedAt(dto.getServerUpdatedAt());
                if (dto.getIsActive() == null) {
                    entity.setIsActive(true);
                }
            }

            return target;
        } catch (Exception e) {
            log.error("Failed to map DTO to Entity: {} - Class: {}", e.getMessage(), source.getClass().getName());
            throw new RuntimeException("Failed to map DTO to Entity", e);
        }
    }

    public static <S, T extends BaseSyncEntity> List<T> mapToEntityList(List<S> sourceList, Class<T> targetClass) {
        if (sourceList == null) return new ArrayList<>();
        List<T> result = new ArrayList<>();
        for (S source : sourceList) {
            T mapped = mapToEntity(source, targetClass);
            if (mapped != null) result.add(mapped);
        }
        return result;
    }
}
