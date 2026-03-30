package com.khanabook.saas.sync.dto.payload;

import lombok.Data;
import java.util.List;

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
}
