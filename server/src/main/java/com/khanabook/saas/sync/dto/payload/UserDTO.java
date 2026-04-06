package com.khanabook.saas.sync.dto.payload;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.khanabook.saas.entity.*;
import java.math.BigDecimal;

@Data
public class UserDTO {
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
    private String email;
    private String loginId;
    private String phoneNumber;
    private String googleEmail;
    private AuthProvider authProvider;
    @JsonIgnore
    private String passwordHash;
    private String whatsappNumber;
    private UserRole role;
    @JsonIgnore
    private Boolean isActive;
    private Long tokenInvalidatedAt;
}
