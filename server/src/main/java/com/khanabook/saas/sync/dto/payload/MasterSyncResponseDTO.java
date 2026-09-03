package com.khanabook.saas.sync.dto.payload;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class MasterSyncResponseDTO {
    private Long serverTimestamp;
    private List<RestaurantProfileDTO> profiles;
    private List<UserDTO> users;
    private List<CategoryDTO> categories;
    private List<MenuItemDTO> menuItems;
    private List<ItemVariantDTO> itemVariants;
    private List<StockLogDTO> stockLogs;
    private List<BillDTO> bills;
    private List<BillItemDTO> billItems;
    private List<BillPaymentDTO> billPayments;
    private Boolean hasMore;
    private Integer nextPage;

    /** Effective feature-flag state for this restaurant (Requirement 30.23). */
    private Map<String, Boolean> enabledFeatures;

    /** Granted permission keys for the requesting user (lightweight sync). */
    private List<String> grantedPermissions;

    /**
     * Monotonic authorization revision for the requesting user (P1). Android stamps
     * this onto locally-created menu edits so the server can run Decision-A-strict
     * revalidation at push time. Null for OWNER/admin or when no user is resolved.
     */
    private Long permissionRevision;
}
