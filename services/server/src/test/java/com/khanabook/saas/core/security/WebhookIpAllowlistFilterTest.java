package com.khanabook.saas.core.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookIpAllowlistFilterTest {

    private static WebhookIpAllowlistFilter filter(String allowedIps) {
        return new WebhookIpAllowlistFilter(allowedIps);
    }

    @Test
    void emptyConfigurationDisablesTheFilter() {
        WebhookIpAllowlistFilter filter = filter("");
        assertTrue(filter.isAllowed("8.8.8.8"), "disabled filter must not reject anything");
        assertTrue(filter.shouldNotFilter(webhookRequest("/payments/easebuzz/webhook")));
    }

    @Test
    void starDisablesExplicitly() {
        assertTrue(filter("*").isAllowed("8.8.8.8"));
    }

    @Test
    void acceptsExactAddressAndRejectsNeighbour() {
        WebhookIpAllowlistFilter filter = filter("203.0.113.7");
        assertTrue(filter.isAllowed("203.0.113.7"));
        assertFalse(filter.isAllowed("203.0.113.8"));
    }

    @Test
    void matchesIpv4CidrBlockBoundaries() {
        WebhookIpAllowlistFilter filter = filter("203.0.113.0/24");
        assertTrue(filter.isAllowed("203.0.113.0"), "network address is inside the block");
        assertTrue(filter.isAllowed("203.0.113.255"), "broadcast address is inside the block");
        assertFalse(filter.isAllowed("203.0.114.1"));
    }

    @Test
    void handlesNonOctetAlignedPrefixes() {
        // /26 covers .64 - .127
        WebhookIpAllowlistFilter filter = filter("198.51.100.64/26");
        assertTrue(filter.isAllowed("198.51.100.64"));
        assertTrue(filter.isAllowed("198.51.100.127"));
        assertFalse(filter.isAllowed("198.51.100.63"));
        assertFalse(filter.isAllowed("198.51.100.128"));
    }

    @Test
    void matchesIpv6AndMappedIpv4() {
        WebhookIpAllowlistFilter v6 = filter("2001:db8::/32");
        assertTrue(v6.isAllowed("2001:db8:1:2::5"));
        assertFalse(v6.isAllowed("2001:db9::1"));

        // A v4 client arriving over a v6 socket is reported as ::ffff:a.b.c.d and must
        // still match a v4 allowlist entry.
        WebhookIpAllowlistFilter v4 = filter("192.0.2.10");
        assertTrue(v4.isAllowed("::ffff:192.0.2.10"));
    }

    @Test
    void multipleEntriesAllMatch() {
        WebhookIpAllowlistFilter filter = filter("10.0.0.0/8, 192.0.2.5 ,2001:db8::1");
        assertTrue(filter.isAllowed("10.9.9.9"));
        assertTrue(filter.isAllowed("192.0.2.5"));
        assertTrue(filter.isAllowed("2001:db8::1"));
        assertFalse(filter.isAllowed("11.0.0.1"));
    }

    @Test
    void rejectsGarbageAndMissingAddresses() {
        WebhookIpAllowlistFilter filter = filter("10.0.0.0/8");
        assertFalse(filter.isAllowed(null));
        assertFalse(filter.isAllowed(""));
        assertFalse(filter.isAllowed("not-an-ip"));
    }

    @Test
    void malformedEntryFailsFastRatherThanSilentlyOpeningTheList() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> filter("10.0.0.0/8,999.1.1.1"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> filter("10.0.0.0/33"));
    }

    @Test
    void filtersAllWebhookPathsAndNothingElse() {
        WebhookIpAllowlistFilter filter = filter("10.0.0.0/8");

        assertFalse(filter.shouldNotFilter(webhookRequest("/payments/easebuzz/webhook")));
        assertFalse(filter.shouldNotFilter(webhookRequest("/payments/easebuzz/refund/webhook")));
        assertFalse(filter.shouldNotFilter(webhookRequest("/payments/easebuzz/sub-merchant/webhook")));
        assertFalse(filter.shouldNotFilter(webhookRequest("/payments/easebuzz/payout/webhook")));

        // Context path must be stripped, not treated as part of the route.
        MockHttpServletRequest contextual = webhookRequest("/api/v1/payments/easebuzz/webhook");
        contextual.setContextPath("/api/v1");
        assertFalse(filter.shouldNotFilter(contextual));

        assertTrue(filter.shouldNotFilter(webhookRequest("/payments/easebuzz/status/42")));
        assertTrue(filter.shouldNotFilter(webhookRequest("/payments/easebuzz/return")));
        assertTrue(filter.shouldNotFilter(webhookRequest("/sync/push")));
    }

    @Test
    void matchesPathsCarryingMatrixParameters() {
        WebhookIpAllowlistFilter filter = filter("10.0.0.0/8");

        // Spring MVC strips path parameters before routing, so these still reach the
        // webhook handler and must not slip past the allowlist.
        assertFalse(filter.shouldNotFilter(webhookRequest("/payments/easebuzz/webhook;jsessionid=abc")));
        assertFalse(filter.shouldNotFilter(webhookRequest("/payments/easebuzz/refund;x=1/webhook")));
        assertFalse(filter.shouldNotFilter(webhookRequest("/payments/easebuzz/webhook%3Bjsessionid=abc")));
    }

    private static MockHttpServletRequest webhookRequest(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }
}
