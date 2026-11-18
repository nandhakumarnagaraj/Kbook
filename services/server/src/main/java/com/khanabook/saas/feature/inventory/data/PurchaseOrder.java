package com.khanabook.saas.feature.inventory.data;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated Retired in V127. POS operates on finished MenuItem and ItemVariant catalog records.
 */
@Deprecated
@Getter
@Setter
public class PurchaseOrder {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_RECEIVED = "RECEIVED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private Long id;
    private Long restaurantId;
    private Long vendorId;
    private String status = STATUS_DRAFT;
    private String note;
    private List<PurchaseOrderItem> items = new ArrayList<>();
    private Long createdAt;
    private Long updatedAt;
}
