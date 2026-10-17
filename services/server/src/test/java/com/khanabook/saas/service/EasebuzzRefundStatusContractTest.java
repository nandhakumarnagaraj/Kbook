package com.khanabook.saas.service;

import com.khanabook.saas.feature.payments.service.EasebuzzApiClient;
import com.khanabook.saas.feature.payments.service.EasebuzzProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class EasebuzzRefundStatusContractTest {
    @Test
    void sendsJsonStatusLookupWithPaymentHashAndPreservesRefunds() throws Exception {
        EasebuzzProperties props = new EasebuzzProperties();
        props.setMerchantKey("test-key");
        props.setSalt("test-salt");
        props.setDashboardBaseUrl("https://dashboard.easebuzz.in");
        EasebuzzApiClient client = new EasebuzzApiClient(props);
        RestTemplate rest = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512")
                .digest("test-key|E_TEST|test-salt".getBytes(StandardCharsets.UTF_8)));
        server.expect(requestTo("https://dashboard.easebuzz.in/refund/v1/retrieve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"key\":\"test-key\",\"easebuzz_id\":\"E_TEST\",\"merchant_refund_id\":\"R_TEST\",\"hash\":\"" + hash + "\"}", true))
                .andRespond(withSuccess("{\"status\":true,\"easebuzz_id\":\"E_TEST\",\"refunds\":[{\"refund_id\":\"R_TEST\",\"refund_status\":\"queued\"}]}", MediaType.APPLICATION_JSON));
        var result = client.getRefundStatus("E_TEST", "R_TEST");
        assertEquals(Boolean.TRUE, result.get("status"));
        assertNotNull(result.get("refunds"));
        server.verify();
        assertThrows(IllegalArgumentException.class, () -> client.getRefundStatus(" ", null));
    }
}
