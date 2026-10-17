package com.khanabook.saas.feature.menu.data;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class CategoryDTO {
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
    private Integer sortOrder;
    private Boolean isActive;
    /** Client-owned: the app sends this, and while it was absent it was dropped on every push. */
    private Boolean isVeg;

    /**
     * Server-authoritative pin (see Category#isFavourite). Delivered on pull so both
     * surfaces can render the star; a value sent by a client is ignored on update and
     * only defaults the column on create.
     */
    @JsonProperty("isFavourite")
    private Boolean isFavourite;
}
