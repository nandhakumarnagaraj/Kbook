package com.khanabook.saas.feature.inventory.data;

import lombok.Getter;
import lombok.Setter;

/**
 * @deprecated Retired in V127. POS operates on finished MenuItem and ItemVariant catalog records.
 */
@Deprecated
@Getter
@Setter
public class Vendor {

    private Long id;
    private Long restaurantId;
    private String name;
    private String phone;
    private String notes;
    private Boolean isDeleted = false;
    private Long createdAt;
    private Long updatedAt;
}
