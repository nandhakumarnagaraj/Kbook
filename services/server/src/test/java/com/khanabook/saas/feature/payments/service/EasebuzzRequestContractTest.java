package com.khanabook.saas.feature.payments.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Asserts the bytes KhanaBook actually puts on the wire for each Easebuzz call, not merely that a
 * client method was invoked.
 *
 * <p>Every expected value here is taken from Easebuzz's own integration kit
 * (github.com/easebuzz/paywitheasebuzz-php-lib): {@code initiate_payment.php},
 * {@code transaction.php}, {@code refund.php} and {@code utils.php} for the hash sequences. The
 * hashes are recomputed independently in this test, so a reordering of the hash inputs fails here
 * instead of surfacing as a rejected payment in production.
 */
class EasebuzzRequestContractTest {

    private static final String KEY = "test-key";
    private static final String SALT = "test-salt";

    private static EasebuzzApiClient client() {
        EasebuzzProperties props = new EasebuzzProperties();
        props.setMerchantKey(KEY);
        props.setSalt(SALT);
        props.setPaymentBaseUrl("https://testpay.easebuzz.in");
        props.setDashboardBaseUrl("https://dashboard.easebuzz.in");
        props.setReturnUrl("https://kb.test/easebuzz/return");
        props.setNotifyUrl("https://kb.test/easebuzz/webhook");
        return new EasebuzzApiClient(props);
    }

    private static MockRestServiceServer bind(EasebuzzApiClient client) {
        RestTemplate rest = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        return MockRestServiceServer.bindTo(rest).build();
    }

    private static String sha512(String input) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-512").digest(input.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void initiatePaymentSendsTheDocumentedFormRequestAndHash() throws Exception {
        EasebuzzApiClient client = client();
        MockRestServiceServer server = bind(client);

        Map<String, String> data = new HashMap<>();
        data.put("txnid", "TXN123");
        data.put("amount", "250.00");
        data.put("productinfo", "Order123");
        data.put("firstname", "Ravi");
        data.put("email", "r@kb.test");
        data.put("phone", "9999999999");
        data.put("udf1", "1");
        data.put("udf2", "100");

        // key|txnid|amount|productinfo|firstname|email|udf1..udf10|salt
        String expectedHash = sha512(String.join("|",
                KEY, "TXN123", "250.00", "Order123", "Ravi", "r@kb.test",
                "1", "100", "", "", "", "", "", "", "", "") + "|" + SALT);

        String accessKey = "a".repeat(64);
        server.expect(requestTo("https://testpay.easebuzz.in/payment/initiateLink"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("key=" + KEY)))
                .andExpect(content().string(containsString("txnid=TXN123")))
                .andExpect(content().string(containsString("amount=250.00")))
                .andExpect(content().string(containsString("productinfo=Order123")))
                .andExpect(content().string(containsString("firstname=Ravi")))
                .andExpect(content().string(containsString("email=r%40kb.test")))
                .andExpect(content().string(containsString("udf1=1")))
                .andExpect(content().string(containsString("udf10=")))
                .andExpect(content().string(containsString("hash=" + expectedHash)))
                .andRespond(withSuccess("{\"status\":1,\"data\":\"" + accessKey + "\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.initiatePayment(data);

        assertEquals("success", result.get("status"));
        assertEquals(accessKey, result.get("access_token"));
        assertEquals("https://testpay.easebuzz.in/pay/" + accessKey, result.get("payment_url"));
        server.verify();
    }

    @Test
    void initiatePaymentRefusesAnAccessKeyThatCannotBuildACheckoutUrl() throws Exception {
        EasebuzzApiClient client = client();
        MockRestServiceServer server = bind(client);

        Map<String, String> data = new HashMap<>();
        data.put("txnid", "TXN123");
        data.put("amount", "250.00");
        data.put("productinfo", "Order123");
        data.put("firstname", "Ravi");
        data.put("email", "r@kb.test");
        data.put("phone", "9999999999");

        server.expect(requestTo("https://testpay.easebuzz.in/payment/initiateLink"))
                .andRespond(withSuccess("{\"status\":1,\"data\":\"too-short\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.initiatePayment(data);

        assertEquals("failure", result.get("status"));
        assertTrue(String.valueOf(result.get("error")).toLowerCase().contains("access key"));
        server.verify();
    }

    @Test
    void initiateRefundSendsDocumentedFormRequestAndHash() throws Exception {
        EasebuzzApiClient client = client();
        MockRestServiceServer server = bind(client);

        // key|merchant_refund_id|easebuzz_id|refund_amount|salt
        String expectedHash = sha512(KEY + "|REF_1_0|EZPAY0001|100.00|" + SALT);

        server.expect(requestTo("https://dashboard.easebuzz.in/transaction/v2/refund"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("key=" + KEY)))
                .andExpect(content().string(containsString("merchant_refund_id=REF_1_0")))
                .andExpect(content().string(containsString("easebuzz_id=EZPAY0001")))
                .andExpect(content().string(containsString("refund_amount=100.00")))
                .andExpect(content().string(containsString("hash=" + expectedHash)))
                .andRespond(withSuccess("{\"status\":true,\"refund_id\":\"RFND1\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.initiateRefund("REF_1_0", "EZPAY0001", "100.00");

        assertEquals(Boolean.TRUE, result.get("status"));
        assertEquals("RFND1", result.get("refund_id"));
        server.verify();
    }

    @Test
    void transactionStatusFallsBackToTheV2RouteWhenV21IsAbsent() throws Exception {
        EasebuzzApiClient client = client();
        MockRestServiceServer server = bind(client);

        String expectedHash = sha512(KEY + "|TXN123|" + SALT);

        server.expect(requestTo("https://dashboard.easebuzz.in/transaction/v2.1/retrieve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("hash=" + expectedHash)))
                .andRespond(request -> new MockClientHttpResponse(
                        "{}".getBytes(StandardCharsets.UTF_8), HttpStatus.NOT_FOUND));

        server.expect(requestTo("https://dashboard.easebuzz.in/transaction/v2/retrieve"))
                .andRespond(withSuccess("{\"status\":true,\"msg\":{\"txnid\":\"TXN123\",\"status\":\"success\"}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.getTransactionStatus("TXN123");

        assertEquals(Boolean.TRUE, result.get("status"));
        assertNotNull(result.get("msg"));
        server.verify();
    }

    @Test
    void settlementsAskForTheMaximumPageSizeAndFollowTheNextCursor() throws Exception {
        EasebuzzApiClient client = client();
        MockRestServiceServer server = bind(client);

        // key|start_date|end_date|salt, dates in DD-MM-YYYY.
        String expectedHash = sha512(KEY + "|01-10-2026|02-10-2026|" + SALT);

        server.expect(requestTo("https://dashboard.easebuzz.in/settlements/v1/retrieve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(
                        containsString("\"page_size\":500"),
                        containsString("\"start_date\":\"01-10-2026\""),
                        containsString("\"end_date\":\"02-10-2026\""),
                        containsString("\"hash\":\"" + expectedHash + "\""),
                        not(containsString("\"page\":")))))
                .andRespond(withSuccess(
                        "{\"status\":true,\"next\":\"2\",\"count\":2,\"data\":[{\"payout_id\":\"P1\"}]}",
                        MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://dashboard.easebuzz.in/settlements/v1/retrieve"))
                .andExpect(content().string(containsString("\"page\":\"2\"")))
                .andRespond(withSuccess(
                        "{\"status\":true,\"next\":null,\"count\":2,\"data\":[{\"payout_id\":\"P2\"}]}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.retrieveSettlements("2026-10-01", "2026-10-02", null);

        assertEquals(2, result.get("pagesFetched"));
        assertEquals(false, result.get("truncated"));
        assertEquals(2, ((List<?>) result.get("data")).size());
        server.verify();
    }

    @Test
    void createSubMerchantOmitsAnIdTheGatewayHasNotIssued() throws Exception {
        EasebuzzApiClient client = client();
        MockRestServiceServer server = bind(client);

        // Easebuzz issues sub_merchant_id; KhanaBook must not invent one and send it as if it
        // were the gateway's identifier.
        server.expect(requestTo("https://dashboard.easebuzz.in/merchant/v1/submerchant/create/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(not(containsString("sub_merchant_id"))))
                .andRespond(withSuccess(
                        "{\"status\":true,\"submerchant\":{\"submerchant_id\":\"S360NESTED\"}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.createSubMerchant(
                null, "Spice Hub", "owner@spice.test", "9999999999",
                "1234567890", "HDFC0000123", "HDFC", "Spice Hub", "MG Road",
                "SOLE_PROPRIETORSHIP", "ABCDE1234F", "29AABCU9603R1ZM",
                "12 Residency Road, Bengaluru", "Spice Hub Pvt Ltd", "Karnataka",
                "12345678901234", "DINE_IN", null, null, "PortalPass123");

        // The documented response nests the generated id; the service must find it there.
        assertEquals("S360NESTED", SubMerchantService.extractSubMerchantId(result));
        server.verify();
    }

    @Test
    void createSubMerchantSendsTheDocumentedSectionsAndNeverAKycDetailsBlock() throws Exception {
        EasebuzzApiClient client = client();
        MockRestServiceServer server = bind(client);

        // key|submerchant_email|submerchant_phone|salt
        String expectedHash = sha512(KEY + "|owner@spice.test|9999999999|" + SALT);

        server.expect(requestTo("https://dashboard.easebuzz.in/merchant/v1/submerchant/create/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // All three documented sections, and the hash the kit specifies for this call.
                .andExpect(content().string(allOf(
                        containsString("\"merchant_details\""),
                        containsString("\"submerchant_details\""),
                        containsString("\"business_details\""),
                        containsString("\"merchant_key\":\"" + KEY + "\""),
                        containsString("\"hash\":\"" + expectedHash + "\""),
                        containsString("\"sub_merchant_name\":\"Spice Hub\""),
                        containsString("\"sub_merchant_email\":\"owner@spice.test\""),
                        containsString("\"sub_merchant_phone\":\"9999999999\""),
                        containsString("\"sub_merchant_account_number\":\"1234567890\""),
                        containsString("\"sub_merchant_ifsc_code\":\"HDFC0000123\""),
                        containsString("\"sub_merchant_bank_name\":\"HDFC\""),
                        containsString("\"sub_merchant_name_in_bank\":\"Spice Hub\""),
                        containsString("\"sub_merchant_branch_name\":\"MG Road\""),
                        containsString("\"sub_merchant_id\":\"KBSM000001ABC123\""),
                        containsString("\"sub_merchant_business_name\":\"Spice Hub Pvt Ltd\""),
                        containsString("\"sub_merchant_business_nature\":\"SOLE PROPRIETOR\""),
                        containsString("\"sub_merchant_state\":\"Karnataka\""),
                        containsString("\"sub_merchant_mcc_code\":\"5812\""))))
                // No document block is sent because no first-party schema for it has been confirmed.
                .andExpect(content().string(not(containsString("kyc_details"))))
                // An update carries no password: the documented update payload omits them, and a
                // fresh random one would silently rotate the merchant's Easebuzz dashboard login.
                .andExpect(content().string(not(containsString("sub_merchant_password"))))
                .andExpect(content().string(not(containsString("sub_merchant_confirm_password"))))
                .andRespond(withSuccess("{\"status\":true,\"submerchant_id\":\"S360TEST1\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.createSubMerchant(
                "KBSM000001ABC123", "Spice Hub", "owner@spice.test", "9999999999",
                "1234567890", "HDFC0000123", "HDFC", "Spice Hub", "MG Road",
                "SOLE_PROPRIETORSHIP", "ABCDE1234F", "29AABCU9603R1ZM",
                "12 Residency Road, Bengaluru", "Spice Hub Pvt Ltd", "Karnataka",
                "12345678901234", "DINE_IN", BigDecimal.ZERO, BigDecimal.ZERO, "PortalPass123");

        assertEquals(Boolean.TRUE, result.get("status"));
        assertEquals("S360TEST1", result.get("submerchant_id"));
        server.verify();
    }

    @Test
    void aFirstCreateSendsAPasswordPairAndNoSubMerchantId() {
        EasebuzzApiClient client = client();
        MockRestServiceServer server = bind(client);

        server.expect(requestTo("https://dashboard.easebuzz.in/merchant/v1/submerchant/create/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(
                        containsString("\"sub_merchant_password\""),
                        containsString("\"sub_merchant_confirm_password\""),
                        // No id yet: Easebuzz issues one, and sending an invented value back was
                        // previously rejected by the gateway.
                        not(containsString("\"sub_merchant_id\"")))))
                .andRespond(withSuccess("{\"status\":true,\"submerchant_id\":\"S360NEWID\"}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.createSubMerchant(
                null, "Spice Hub", "owner@spice.test", "9999999999",
                "1234567890", "HDFC0000123", "HDFC", "Spice Hub", "MG Road",
                "SOLE_PROPRIETORSHIP", "ABCDE1234F", "29AABCU9603R1ZM",
                "12 Residency Road, Bengaluru", "Spice Hub Pvt Ltd", "Karnataka",
                "12345678901234", "DINE_IN", BigDecimal.ZERO, BigDecimal.ZERO, "PortalPass123");

        assertEquals("S360NEWID", result.get("submerchant_id"));
        server.verify();
    }

    @Test
    void createPaymentLinkPostsJsonToTheDashboardEasyCollectEndpoint() throws Exception {
        EasebuzzApiClient client = client();
        MockRestServiceServer server = bind(client);

        // key|merchant_txn|name|email|phone|amount|udf1|udf2|udf3|udf4|udf5|message|salt
        String expectedHash = sha512(String.join("|",
                KEY, "KBLINK123", "Ravi", "r@kb.test", "9999999999", "250.00",
                "1", "100", "", "", "", "Payment for KhanaBook Order #1") + "|" + SALT);

        server.expect(requestTo("https://dashboard.easebuzz.in/easycollect/v1/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(allOf(
                        containsString("\"merchant_txn\":\"KBLINK123\""),
                        containsString("\"amount\":\"250.00\""),
                        containsString("\"udf1\":\"1\""),
                        containsString("\"message\":\"Payment for KhanaBook Order #1\""),
                        containsString("\"sub_merchant_id\":\"S280498CAWF\""),
                        containsString("\"hash\":\"" + expectedHash + "\""))))
                .andRespond(withSuccess("{\"status\":true,\"data\":{"
                        + "\"id\":12842792,\"merchant_txn\":\"KBLINK123\","
                        + "\"payment_url\":\"https://testpay.easebuzz.in/easy_collect/abc123\","
                        + "\"state\":\"active\",\"expiry_date\":\"27-10-2026\"}}",
                        MediaType.APPLICATION_JSON));

        Map<String, String> data = new HashMap<>();
        data.put("merchant_txn", "KBLINK123");
        data.put("name", "Ravi");
        data.put("email", "r@kb.test");
        data.put("phone", "9999999999");
        data.put("amount", "250.00");
        data.put("message", "Payment for KhanaBook Order #1");
        data.put("udf1", "1");
        data.put("udf2", "100");
        data.put("sub_merchant_id", "S280498CAWF");

        Map<String, Object> result = client.createPaymentLink(data);

        // data is an object; the link lives at data.payment_url, not data itself.
        assertEquals("success", result.get("status"));
        assertEquals("https://testpay.easebuzz.in/easy_collect/abc123", result.get("payment_url"));
        assertEquals("KBLINK123", result.get("merchant_txn"));
        assertEquals(12842792, result.get("link_id"));
        assertEquals("active", result.get("state"));
        server.verify();
    }

    @Test
    void createPaymentLinkFailsWhenTheSuccessCarriesNoLinkUrl() throws Exception {
        EasebuzzApiClient client = client();
        MockRestServiceServer server = bind(client);

        server.expect(requestTo("https://dashboard.easebuzz.in/easycollect/v1/create"))
                .andRespond(withSuccess("{\"status\":true,\"data\":{\"id\":7}}", MediaType.APPLICATION_JSON));

        Map<String, String> data = new HashMap<>();
        data.put("merchant_txn", "KBLINK124");
        data.put("name", "Ravi");
        data.put("email", "r@kb.test");
        data.put("phone", "9999999999");
        data.put("amount", "1.00");
        data.put("sub_merchant_id", "S280498CAWF");

        Map<String, Object> result = client.createPaymentLink(data);

        assertEquals("failure", result.get("status"));
        assertNull(result.get("payment_url"));
        assertTrue(String.valueOf(result.get("error")).contains("no payment link"));
        server.verify();
    }

    @Test
    void onDemandSettlementPostsTheDocumentedBatchWithAPerTransactionHash() throws Exception {
        EasebuzzApiClient client = client();
        MockRestServiceServer server = bind(client);

        // key|easebuzz_id|merchant_txn_id|amount|salt — one digest per transaction, none at the top.
        String firstHash = sha512(String.join("|", KEY, "S2610ABC", "KB_TXN_1", "250.00") + "|" + SALT);
        String secondHash = sha512(String.join("|", KEY, "S2610XYZ", "KB_TXN_2", "100.50") + "|" + SALT);

        String expectedBody = "{\"key\":\"" + KEY + "\","
                + "\"merchant_email\":\"finance@khanabook.in\","
                + "\"merchant_batch_id\":\"BATCH-1\","
                + "\"description\":\"Friday batch\","
                + "\"transactions\":["
                + "{\"easebuzz_id\":\"S2610ABC\",\"merchant_txn_id\":\"KB_TXN_1\",\"amount\":\"250.00\",\"hash\":\"" + firstHash + "\"},"
                + "{\"easebuzz_id\":\"S2610XYZ\",\"merchant_txn_id\":\"KB_TXN_2\",\"amount\":\"100.50\",\"hash\":\"" + secondHash + "\"}]}";

        server.expect(requestTo("https://dashboard.easebuzz.in/settlements/v1/ondemand/initiate/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(expectedBody))
                .andRespond(withSuccess(
                        "{\"status\":true,\"batch_status\":\"pending\",\"transactions\":["
                                + "{\"easebuzz_id\":\"S2610ABC\",\"status\":\"accepted\",\"error\":\"\"},"
                                + "{\"easebuzz_id\":\"S2610XYZ\",\"status\":\"accepted\",\"error\":\"\"}]}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.initiateOnDemandSettlement("BATCH-1", "Friday batch", List.of(
                Map.of("easebuzz_id", "S2610ABC", "merchant_txn_id", "KB_TXN_1", "amount", "250.00"),
                Map.of("easebuzz_id", "S2610XYZ", "merchant_txn_id", "KB_TXN_2", "amount", "100.50")));

        assertEquals(Boolean.TRUE, result.get("status"));
        assertEquals("pending", result.get("batch_status"));
        server.verify();
    }

    @Test
    void generateKycAccessKeySendsTheDocumentedBodyAndHash() throws Exception {
        EasebuzzApiClient client = client();
        MockRestServiceServer server = bind(client);

        // merchant_key|sub_merchant_id|name|email|phone|salt
        String expectedHash = sha512(KEY + "|S280498XHD5|Spice Hub|owner@spice.test|9999999999|" + SALT);

        server.expect(requestTo("https://dashboard.easebuzz.in/submerchant/v1/generate_kyc_access_key"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"merchant_key\":\"" + KEY + "\",\"sub_merchant_id\":\"S280498XHD5\","
                        + "\"name\":\"Spice Hub\",\"email\":\"owner@spice.test\","
                        + "\"phone\":\"9999999999\",\"hash\":\"" + expectedHash + "\"}", false))
                .andRespond(withSuccess(
                        "{\"status\":true,\"kyc_dashboard_url\":\"https://kyc.easebuzz.in/session/abc?access_key=k1\"}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.generateKycAccessKey(
                "S280498XHD5", "Spice Hub", "owner@spice.test", "9999999999");

        assertEquals("https://kyc.easebuzz.in/session/abc?access_key=k1", result.get("kyc_dashboard_url"));
        server.verify();
    }

    /**
     * A rejected call is only diagnosable if the gateway's own words survive the exception. Before
     * this, every 4xx collapsed into "Easebuzz gateway request failed", which is why a disabled KYC
     * product and a wrong hash looked identical from the server log.
     */
    @Test
    void aRejectedCallReturnsTheGatewaysOwnErrorText() {
        EasebuzzApiClient client = client();
        MockRestServiceServer server = bind(client);

        server.expect(requestTo("https://dashboard.easebuzz.in/submerchant/v1/generate_kyc_access_key"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":false,\"error\":\"KYC service not enabled for this merchant\"}"));

        Map<String, Object> result = client.generateKycAccessKey(
                "S280498XHD5", "Spice Hub", "owner@spice.test", "9999999999");

        assertEquals(Boolean.FALSE, result.get("status"));
        assertTrue(result.get("error").toString().contains("KYC service not enabled"),
                "provider text must survive the exception, got: " + result.get("error"));
    }

    @Test
    void onDemandSettlementRejectsATransactionWithoutItsIdentifyingFields() {
        EasebuzzApiClient client = client();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> client.initiateOnDemandSettlement("BATCH-1", null,
                        List.of(Map.of("easebuzz_id", "S2610ABC"))));
        assertTrue(error.getMessage().contains("easebuzz_id"));
    }
}
