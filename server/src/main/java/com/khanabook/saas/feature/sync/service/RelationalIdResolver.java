package com.khanabook.saas.feature.sync.service;

import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillItem;
import com.khanabook.saas.feature.billing.data.BillPayment;
import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.menu.data.Category;
import com.khanabook.saas.feature.menu.data.ItemVariant;
import com.khanabook.saas.feature.menu.data.ItemRecipe;
import com.khanabook.saas.feature.menu.data.MenuExtractionJob;
import com.khanabook.saas.feature.inventory.data.RawMaterial;
import com.khanabook.saas.feature.inventory.data.PurchaseOrder;
import com.khanabook.saas.feature.inventory.data.PurchaseOrderItem;
import com.khanabook.saas.feature.inventory.data.StockMovement;
import com.khanabook.saas.feature.inventory.data.StockLog;
import com.khanabook.saas.feature.inventory.data.Vendor;
import com.khanabook.saas.feature.inventory.data.CustomerProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminal;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchant;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEvent;
import com.khanabook.saas.feature.payments.data.EasebuzzPayout;
import com.khanabook.saas.feature.payments.data.Chargeback;
import com.khanabook.saas.feature.notifications.data.NotificationEvent;
import com.khanabook.saas.feature.notifications.data.DeviceToken;
import com.khanabook.saas.feature.compliance.data.FssaiTracker;
import com.khanabook.saas.feature.compliance.data.FssaiRenewal;
import com.khanabook.saas.feature.staff.data.StaffPermission;
import com.khanabook.saas.feature.staff.data.StaffPermissionRevision;
import com.khanabook.saas.feature.staff.data.PermissionKey;
import com.khanabook.saas.feature.staff.data.PermissionRequest;
import com.khanabook.saas.feature.staff.data.RoleTemplate;
import com.khanabook.saas.feature.platform.data.FeatureFlag;
import com.khanabook.saas.feature.onboarding.entity.MerchantAgreement;
import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.feature.auth.entity.AuthProvider;
import com.khanabook.saas.feature.auth.entity.RefreshToken;
import com.khanabook.saas.feature.auth.entity.TokenBlocklist;
import com.khanabook.saas.feature.auth.entity.OtpRequest;
import com.khanabook.saas.feature.auth.entity.RateLimitAttempt;
import com.khanabook.saas.feature.auth.entity.SecurityAuditEvent;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.menu.data.MenuItemRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.billing.data.BillItemRepository;
import com.khanabook.saas.feature.billing.data.BillPaymentRepository;
import com.khanabook.saas.feature.menu.data.MenuItemRepository;
import com.khanabook.saas.feature.menu.data.CategoryRepository;
import com.khanabook.saas.feature.menu.data.ItemVariantRepository;
import com.khanabook.saas.feature.menu.data.ItemRecipeRepository;
import com.khanabook.saas.feature.menu.data.MenuExtractionJobRepository;
import com.khanabook.saas.feature.inventory.data.RawMaterialRepository;
import com.khanabook.saas.feature.inventory.data.PurchaseOrderRepository;
import com.khanabook.saas.feature.inventory.data.StockMovementRepository;
import com.khanabook.saas.feature.inventory.data.StockLogRepository;
import com.khanabook.saas.feature.inventory.data.VendorRepository;
import com.khanabook.saas.feature.inventory.data.CustomerProfileRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminalRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchantRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEventRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzPayoutRepository;
import com.khanabook.saas.feature.payments.data.ChargebackRepository;
import com.khanabook.saas.feature.payments.data.WebhookRetryJobRepository;
import com.khanabook.saas.feature.notifications.data.NotificationEventRepository;
import com.khanabook.saas.feature.notifications.data.DeviceTokenRepository;
import com.khanabook.saas.feature.compliance.data.FssaiTrackerRepository;
import com.khanabook.saas.feature.compliance.data.FssaiRenewalRepository;
import com.khanabook.saas.feature.staff.data.StaffPermissionRepository;
import com.khanabook.saas.feature.staff.data.StaffPermissionRevisionRepository;
import com.khanabook.saas.feature.staff.data.PermissionRequestRepository;
import com.khanabook.saas.feature.staff.data.RoleTemplateRepository;
import com.khanabook.saas.feature.platform.data.FeatureFlagRepository;
import com.khanabook.saas.feature.auth.repository.UserRepository;
import com.khanabook.saas.feature.auth.repository.RefreshTokenRepository;
import com.khanabook.saas.feature.auth.repository.TokenBlocklistRepository;
import com.khanabook.saas.feature.auth.repository.OtpRequestRepository;
import com.khanabook.saas.feature.auth.repository.RateLimitAttemptRepository;
import com.khanabook.saas.feature.auth.repository.SecurityAuditLogRepository;
import com.khanabook.saas.feature.sync.data.BaseSyncEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Bulk-resolves device-local FK references to server IDs for a batch of sync records.
 * Eliminates N+1 queries by fetching all referenced localIds per entity type in bulk.
 */
@Component
public class RelationalIdResolver {
    private static final Logger log = LoggerFactory.getLogger(RelationalIdResolver.class);

    private final BillRepository billRepository;
    private final MenuItemRepository menuItemRepository;
    private final ItemVariantRepository itemVariantRepository;
    private final CategoryRepository categoryRepository;

    public RelationalIdResolver(BillRepository billRepository,
                                 MenuItemRepository menuItemRepository,
                                 ItemVariantRepository itemVariantRepository,
                                 CategoryRepository categoryRepository) {
        this.billRepository = billRepository;
        this.menuItemRepository = menuItemRepository;
        this.itemVariantRepository = itemVariantRepository;
        this.categoryRepository = categoryRepository;
    }

    public static class IdMaps {
        Map<Long, Long> billLocalToServerId      = new HashMap<>();
        Map<Long, Long> menuItemLocalToServerId  = new HashMap<>();
        Map<Long, Long> variantLocalToServerId   = new HashMap<>();
        Map<Long, Long> categoryLocalToServerId  = new HashMap<>();
    }

    /**
     * Builds lookup maps for all FK localIds present in a device batch.
     * ONE bulk query per entity type instead of one query per record.
     */
    public IdMaps buildMaps(List<? extends BaseSyncEntity> devicePayload,
                            Long tenantId, String deviceId) {
        Set<Long> billLocalIds      = new HashSet<>();
        Set<Long> menuItemLocalIds  = new HashSet<>();
        Set<Long> variantLocalIds   = new HashSet<>();
        Set<Long> categoryLocalIds  = new HashSet<>();

        for (BaseSyncEntity record : devicePayload) {
            if (record instanceof BillItem bi) {
                if (bi.getBillId()     != null) billLocalIds.add(bi.getBillId());
                if (bi.getMenuItemId() != null) menuItemLocalIds.add(bi.getMenuItemId());
                if (bi.getVariantId()  != null) variantLocalIds.add(bi.getVariantId());
            } else if (record instanceof BillPayment bp) {
                if (bp.getBillId()     != null) billLocalIds.add(bp.getBillId());
            } else if (record instanceof ItemVariant iv) {
                if (iv.getMenuItemId() != null) menuItemLocalIds.add(iv.getMenuItemId());
            } else if (record instanceof MenuItem mi) {
                if (mi.getCategoryId() != null) categoryLocalIds.add(mi.getCategoryId());
            } else if (record instanceof StockLog sl) {
                if (sl.getMenuItemId() != null) menuItemLocalIds.add(sl.getMenuItemId());
                if (sl.getVariantId()  != null) variantLocalIds.add(sl.getVariantId());
            }
        }

        IdMaps maps = new IdMaps();

        if (!billLocalIds.isEmpty()) {
            maps.billLocalToServerId = buildMergedMap(
                    billRepository.findByRestaurantIdAndLocalIdIn(tenantId, new ArrayList<>(billLocalIds)),
                    billRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(tenantId, deviceId, new ArrayList<>(billLocalIds)));
        }
        if (!menuItemLocalIds.isEmpty()) {
            maps.menuItemLocalToServerId = buildMergedMap(
                    menuItemRepository.findByRestaurantIdAndLocalIdIn(tenantId, new ArrayList<>(menuItemLocalIds)),
                    menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(tenantId, deviceId, new ArrayList<>(menuItemLocalIds)));
        }
        if (!variantLocalIds.isEmpty()) {
            maps.variantLocalToServerId = buildMergedMap(
                    itemVariantRepository.findByRestaurantIdAndLocalIdIn(tenantId, new ArrayList<>(variantLocalIds)),
                    itemVariantRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(tenantId, deviceId, new ArrayList<>(variantLocalIds)));
        }
        if (!categoryLocalIds.isEmpty()) {
            maps.categoryLocalToServerId = buildMergedMap(
                    categoryRepository.findByRestaurantIdAndLocalIdIn(tenantId, new ArrayList<>(categoryLocalIds)),
                    categoryRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(tenantId, deviceId, new ArrayList<>(categoryLocalIds)));
        }

        return maps;
    }

    /**
     * Resolves all FK localIds on a record to server IDs using pre-built maps.
     */
    public void resolve(BaseSyncEntity record, IdMaps maps) {
        try {
            if (record instanceof MenuItem menuItem) {
                if (menuItem.getCategoryId() != null) {
                    Long serverId = maps.categoryLocalToServerId.get(menuItem.getCategoryId());
                    if (serverId != null) {
                        menuItem.setServerCategoryId(serverId);
                        menuItem.setCategoryId(serverId);
                    }
                }
            } else if (record instanceof ItemVariant variant) {
                if (variant.getMenuItemId() != null) {
                    Long serverId = maps.menuItemLocalToServerId.get(variant.getMenuItemId());
                    if (serverId != null) {
                        variant.setServerMenuItemId(serverId);
                        variant.setMenuItemId(serverId);
                    }
                }
            } else if (record instanceof BillItem billItem) {
                if (billItem.getBillId() != null) {
                    Long serverId = maps.billLocalToServerId.get(billItem.getBillId());
                    if (serverId != null) {
                        billItem.setServerBillId(serverId);
                        billItem.setBillId(serverId);
                    }
                }
                if (billItem.getMenuItemId() != null) {
                    Long serverId = maps.menuItemLocalToServerId.get(billItem.getMenuItemId());
                    if (serverId != null) {
                        billItem.setServerMenuItemId(serverId);
                        billItem.setMenuItemId(serverId);
                    }
                }
                if (billItem.getVariantId() != null) {
                    Long serverId = maps.variantLocalToServerId.get(billItem.getVariantId());
                    if (serverId != null) {
                        billItem.setServerVariantId(serverId);
                        billItem.setVariantId(serverId);
                    }
                }
            } else if (record instanceof BillPayment payment) {
                if (payment.getBillId() != null) {
                    Long serverId = maps.billLocalToServerId.get(payment.getBillId());
                    if (serverId != null) {
                        payment.setServerBillId(serverId);
                        payment.setBillId(serverId);
                    }
                }
            } else if (record instanceof StockLog logRecord) {
                if (logRecord.getMenuItemId() != null) {
                    Long serverId = maps.menuItemLocalToServerId.get(logRecord.getMenuItemId());
                    if (serverId != null) {
                        logRecord.setServerMenuItemId(serverId);
                        logRecord.setMenuItemId(serverId);
                    }
                }
                if (logRecord.getVariantId() != null) {
                    Long serverId = maps.variantLocalToServerId.get(logRecord.getVariantId());
                    if (serverId != null) {
                        logRecord.setServerVariantId(serverId);
                        logRecord.setVariantId(serverId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Resolution Failed: {}", e.getMessage(), e);
        }
    }

    private Map<Long, Long> buildMergedMap(List<? extends BaseSyncEntity> crossDevice,
                                           List<? extends BaseSyncEntity> deviceSpecific) {
        Map<Long, Long> map = new HashMap<>();
        for (BaseSyncEntity e : crossDevice) {
            if (e.getLocalId() != null && e.getId() != null) map.put(e.getLocalId(), e.getId());
        }
        for (BaseSyncEntity e : deviceSpecific) {
            if (e.getLocalId() != null && e.getId() != null) map.put(e.getLocalId(), e.getId());
        }
        return map;
    }
}
