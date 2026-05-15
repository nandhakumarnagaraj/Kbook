package com.khanabook.saas.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "easebuzz")
@Getter
@Setter
public class EasebuzzProperties {

    private String apiBaseUrl = "https://api.easebuzz.in";
    private String merchantKey;
    private String salt;
    private String returnUrl;
    private String notifyUrl;
    private String webhookUrl;
}
