package com.khanabook.saas.sync.dto.payload;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class RestaurantProfileDTO {
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

    private String name;
    private String address;
    private String phone;
    private String email;
    private String gstNumber;
    private String fssaiNumber;
    private String logoUrl;
    private String website;
    private String tagline;
    private Long dailyOrderCounter;
    private Long lifetimeOrderCounter;
    private String lastResetDate;
}
