package com.khanabook.saas.integration.swiggy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class SwiggyApiClient {

    @Value("${swiggy.api-base-url:https://api.swiggy.com}")
    private String apiBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode getOrderDetails(String apiKey, String orderId) {
        return get("/api/v1/orders/" + orderId, apiKey);
    }

    public JsonNode updateOrderStatus(String apiKey, String orderId, String status) {
        String body = String.format("{\"status\": \"%s\"}", status);
        return post("/api/v1/orders/" + orderId + "/status", apiKey, body);
    }

    public JsonNode acknowledgeOrder(String apiKey, String orderId) {
        return post("/api/v1/orders/" + orderId + "/acknowledge", apiKey, "{}");
    }

    private JsonNode get(String path, String apiKey) {
        return exchange(path, apiKey, HttpMethod.GET, null);
    }

    private JsonNode post(String path, String apiKey, String body) {
        return exchange(path, apiKey, HttpMethod.POST, body);
    }

    private JsonNode exchange(String path, String apiKey, HttpMethod method, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);

        HttpEntity<String> entity = body != null ? new HttpEntity<>(body, headers) : new HttpEntity<>(headers);

        String url = apiBaseUrl.endsWith("/") ? apiBaseUrl + path.substring(1) : apiBaseUrl + path;
        ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Swiggy API request failed: " + response.getStatusCode());
        }

        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse Swiggy response", e);
        }
    }
}
