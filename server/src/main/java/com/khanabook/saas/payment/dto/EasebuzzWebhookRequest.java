package com.khanabook.saas.payment.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class EasebuzzWebhookRequest {
    private Map<String, String> payload;
}
