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
}
