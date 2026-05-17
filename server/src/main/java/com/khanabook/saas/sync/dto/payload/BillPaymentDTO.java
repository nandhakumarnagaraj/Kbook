package com.khanabook.saas.sync.dto.payload;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class BillPaymentDTO {
    @JsonProperty("serverId")
    private Long id;

    @JsonProperty("localId")
    private Long localId;

    private String deviceId;
    private Long restaurantId;
    private Long updatedAt;
    private Boolean isDeleted;
    private Long serverUpdatedAt;
    private Long createdAt;

    private Long billId;
    @JsonProperty("billLocalId")
    private Long billLocalId;
    private Long serverBillId;

    private java.math.BigDecimal amount;
    private String paymentMode;
    private Long paidAt;
}
