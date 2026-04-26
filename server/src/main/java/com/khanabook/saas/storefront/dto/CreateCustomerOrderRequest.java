package com.khanabook.saas.storefront.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateCustomerOrderRequest {

    @NotBlank
    @Size(max = 120)
    private String customerName;

    @Pattern(regexp = "^$|^\\d{10}$", message = "Customer phone must be exactly 10 digits")
    private String customerPhone;

    @Size(max = 1000)
    private String customerNote;

    @NotBlank
    private String fulfillmentType;

    @NotBlank
    private String paymentMethod;

    @NotEmpty
    @Valid
    private List<LineItem> items;

    @Data
    public static class LineItem {
        @Min(1)
        private Long menuItemId;

        private Long variantId;

        @Min(1)
        private Integer quantity;

        @Size(max = 500)
        private String specialInstruction;
    }
}
