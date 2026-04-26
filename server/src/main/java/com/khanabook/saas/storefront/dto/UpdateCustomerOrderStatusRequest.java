package com.khanabook.saas.storefront.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateCustomerOrderStatusRequest {
    @NotBlank
    private String orderStatus;

    @Size(max = 1000)
    private String customerNote;
}
