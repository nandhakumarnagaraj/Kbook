package com.khanabook.saas.feature.payments.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EasebuzzApiClientRetryTest {

    private static EasebuzzApiClient client() {
        EasebuzzProperties props = new EasebuzzProperties();
        props.setMerchantKey("test-key");
        props.setSalt("test-salt");
        props.setDashboardBaseUrl("https://dashboard.easebuzz.in");
        props.setPaymentBaseUrl("https://testpay.easebuzz.in");
        return new EasebuzzApiClient(props);
    }

    private static MockRestServiceServer bind(EasebuzzApiClient client) {
        RestTemplate rest = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        return MockRestServiceServer.bindTo(rest).build();
    }

    @Test
    void retriesTransientServerErrorOnAReadOnlyLookup() {
        EasebuzzApiClient client = client();
        MockRestServiceServer server = bind(client);

        server.expect(requestTo("https://dashboard.easebuzz.in/transaction/v2.1/retrieve"))
                .andRespond(request -> new MockClientHttpResponse(
                        "{}".getBytes(StandardCharsets.UTF_8), HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://dashboard.easebuzz.in/transaction/v2.1/retrieve"))
                .andRespond(withSuccess("{\"status\":true,\"txnid\":\"TXN1\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.getTransactionStatus("TXN1");

        assertEquals(Boolean.TRUE, result.get("status"));
        server.verify();
    }

    @Test
    void doesNotRetryOperationsThatCouldCreateDuplicates() {
        EasebuzzApiClient client = client();
        MockRestServiceServer server = bind(client);
        AtomicInteger attempts = new AtomicInteger();

        // Creating a link is side-effecting and carries no caller-supplied idempotency key.
        server.expect(requestTo("https://dashboard.easebuzz.in/easycollect/v1/create"))
                .andRespond(request -> {
                    attempts.incrementAndGet();
                    return new MockClientHttpResponse(
                            "{}".getBytes(StandardCharsets.UTF_8), HttpStatus.SERVICE_UNAVAILABLE);
                });

        Map<String, String> data = new HashMap<>();
        data.put("merchant_txn", "KBLINK1");
        data.put("name", "Ravi");
        data.put("email", "r@kb.test");
        data.put("phone", "9999999999");
        data.put("amount", "1.00");

        Map<String, Object> result = client.createPaymentLink(data);

        assertEquals(1, attempts.get(), "a non-idempotent call must be attempted exactly once");
        assertEquals("failure", result.get("status"));
        assertFalse(String.valueOf(result.get("error")).isBlank());
    }
}
