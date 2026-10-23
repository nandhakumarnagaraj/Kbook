package com.khanabook.saas.feature.payments.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in live check against the real Easebuzz gateway.
 *
 * <p>Runs only when {@code EASEBUZZ_LIVE_UAT=1} and the key/salt are in the environment, so an
 * ordinary build never touches the network. It is the driver for the live UAT the contract tests
 * cannot replace: those pin our bytes, this proves the gateway accepts them.
 *
 * <pre>
 * set -a; . ./env/.env; set +a
 * EASEBUZZ_LIVE_UAT=1 ./mvnw -o test -Dtest=EasebuzzLiveUatTest
 * </pre>
 *
 * Optional: {@code EASEBUZZ_UAT_SUB_MERCHANT_ID} (required on a sub-aggregator account),
 * {@code EASEBUZZ_DASHBOARD_BASE_URL} / {@code EASEBUZZ_PAYMENT_BASE_URL} to pin the sandbox.
 */
@EnabledIfEnvironmentVariable(named = "EASEBUZZ_LIVE_UAT", matches = "1")
class EasebuzzLiveUatTest {

    @Test
    void createsARealEasyCollectLink() {
        EasebuzzProperties props = new EasebuzzProperties();
        props.setMerchantKey(System.getenv("EASEBUZZ_MERCHANT_KEY"));
        props.setSalt(System.getenv("EASEBUZZ_SALT"));
        props.setDashboardBaseUrl(System.getenv().getOrDefault(
                "EASEBUZZ_DASHBOARD_BASE_URL", "https://dashboard.easebuzz.in"));
        props.setPaymentBaseUrl(System.getenv().getOrDefault(
                "EASEBUZZ_PAYMENT_BASE_URL", "https://pay.easebuzz.in"));

        String merchantTxn = "KBUAT" + System.currentTimeMillis();
        Map<String, String> data = new HashMap<>();
        data.put("merchant_txn", merchantTxn);
        data.put("name", "KhanaBook UAT");
        data.put("email", "uat@khanabook.in");
        data.put("phone", "9999999999");
        data.put("amount", "1.00");
        data.put("message", "KhanaBook EasyCollect UAT " + merchantTxn);

        String subMerchantId = System.getenv("EASEBUZZ_UAT_SUB_MERCHANT_ID");
        if (subMerchantId != null && !subMerchantId.isBlank()) {
            data.put("sub_merchant_id", subMerchantId);
        }

        Map<String, Object> result = new EasebuzzApiClient(props).createPaymentLink(data);
        System.out.println("[UAT] createPaymentLink " + merchantTxn + " -> " + result);

        assertEquals("success", result.get("status"), "gateway rejected the link: " + result.get("error"));
        assertNotNull(result.get("payment_url"));
        assertTrue(String.valueOf(result.get("payment_url")).startsWith("http"));
    }
}
