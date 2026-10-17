package com.khanabook.saas.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Source-IP allowlist for the Easebuzz server-to-server webhooks.
 *
 * <p>The webhook endpoints are {@code permitAll} in {@link SecurityConfig} because the
 * gateway cannot present a bearer token; authenticity comes from the reverse hash that
 * {@code EasebuzzWebhookService} verifies. The hash proves <em>who signed</em> the
 * payload but does nothing to stop a flood of unauthenticated requests from arbitrary
 * internet hosts, each of which costs a signature computation and a DB lookup. This
 * filter adds the network-level control Easebuzz's onboarding checklist asks for.
 *
 * <p>Configuration (property {@code easebuzz.webhook.allowed-ips}, env
 * {@code EASEBUZZ_WEBHOOK_ALLOWED_IPS}) is a comma separated list of IPv4/IPv6
 * addresses and CIDR blocks, e.g. {@code 203.0.113.0/24,2001:db8::/32}. An empty
 * value leaves the filter disabled so a deployment cannot lose live payment
 * callbacks by upgrading without the property set; startup then logs a warning.
 * Set it to {@code *} to state that intent explicitly.
 *
 * <p>Client IP comes from {@code request.getRemoteAddr()} — the same source
 * {@link JwtRequestFilter} trusts for the admin allowlist. Spring's
 * {@code server.forward-headers-strategy=framework} resolves it from
 * {@code X-Forwarded-For} only for proxies configured as trusted, so a client
 * cannot spoof its way in with a header.
 */
@Component
public class WebhookIpAllowlistFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(WebhookIpAllowlistFilter.class);

    /** {@code /payments/easebuzz/webhook} and the per-flow variants beneath it. */
    private static final Pattern WEBHOOK_PATH =
            Pattern.compile("^/payments/easebuzz(/[A-Za-z0-9_-]+)?/webhook$");

    /**
     * Matrix/path parameters, e.g. {@code ;jsessionid=abc} or its percent-encoded form.
     * Spring MVC strips these before routing, so {@code .../refund;x/webhook} still
     * reaches the refund handler. A filter that matched only the literal path would
     * skip the request precisely when the check matters, so segments are normalised
     * before matching.
     */
    private static final Pattern PATH_PARAMETERS = Pattern.compile("(;|%3[Bb])[^/]*");

    private final List<Subnet> allowedSubnets;
    private final boolean disabled;

    public WebhookIpAllowlistFilter(@Value("${easebuzz.webhook.allowed-ips:}") String rawAllowedIps) {
        boolean explicitlyDisabled = rawAllowedIps != null && "*".equals(rawAllowedIps.trim());
        List<Subnet> parsed = parse(rawAllowedIps);
        this.disabled = explicitlyDisabled || parsed.isEmpty();
        this.allowedSubnets = List.copyOf(parsed);

        if (disabled) {
            log.warn("Easebuzz webhook IP allowlist is DISABLED. Set easebuzz.webhook.allowed-ips "
                    + "(EASEBUZZ_WEBHOOK_ALLOWED_IPS) to the gateway's published ranges before go-live.");
        } else {
            log.info("Easebuzz webhook IP allowlist enabled with {} entr{}",
                    allowedSubnets.size(), allowedSubnets.size() == 1 ? "y" : "ies");
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (disabled) {
            return true;
        }
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        path = PATH_PARAMETERS.matcher(path).replaceAll("");
        return !WEBHOOK_PATH.matcher(path).matches();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = request.getRemoteAddr();
        if (isAllowed(clientIp)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Rejected Easebuzz webhook from non-allowlisted IP={} path={}", clientIp, request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":\"failure\",\"error\":\"webhook_source_not_allowed\"}");
    }

    boolean isAllowed(String clientIp) {
        if (disabled) {
            return true;
        }
        if (clientIp == null || clientIp.isBlank()) {
            return false;
        }
        byte[] address = parseAddress(clientIp);
        if (address == null) {
            return false;
        }
        for (Subnet subnet : allowedSubnets) {
            if (subnet.contains(address)) {
                return true;
            }
        }
        return false;
    }

    private static List<Subnet> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Subnet> subnets = new ArrayList<>();
        for (String entry : Arrays.asList(raw.split(","))) {
            String token = entry.trim();
            if (token.isEmpty() || "*".equals(token)) {
                continue;
            }
            try {
                subnets.add(Subnet.parse(token));
            } catch (IllegalArgumentException e) {
                // Fail closed on a malformed entry: silently dropping it would open
                // exactly the hole the operator was trying to close.
                throw new IllegalStateException(
                        "Invalid easebuzz.webhook.allowed-ips entry '" + token + "': " + e.getMessage(), e);
            }
        }
        return subnets;
    }

    private static byte[] parseAddress(String ip) {
        try {
            return InetAddress.getByName(ip).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /** A single IP or CIDR block, normalised to 16-byte (IPv6) form for comparison. */
    private static final class Subnet {
        private final byte[] network;
        private final int prefixBits;

        private Subnet(byte[] network, int prefixBits) {
            this.network = network;
            this.prefixBits = prefixBits;
        }

        static Subnet parse(String token) {
            String addressPart = token;
            Integer prefix = null;
            int slash = token.indexOf('/');
            if (slash >= 0) {
                addressPart = token.substring(0, slash);
                try {
                    prefix = Integer.parseInt(token.substring(slash + 1).trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("prefix length is not a number");
                }
            }

            byte[] address = parseAddress(addressPart.trim());
            if (address == null) {
                throw new IllegalArgumentException("not an IP address");
            }

            int nativeBits = address.length * 8;
            if (prefix == null) {
                prefix = nativeBits;
            }
            if (prefix < 0 || prefix > nativeBits) {
                throw new IllegalArgumentException("prefix length out of range for " + nativeBits + "-bit address");
            }

            byte[] normalised = toComparable(address);
            int normalisedPrefix = address.length == 4 ? prefix + 96 : prefix;
            return new Subnet(normalised, normalisedPrefix);
        }

        boolean contains(byte[] candidate) {
            byte[] address = toComparable(candidate);
            int fullBytes = prefixBits / 8;
            int remainder = prefixBits % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != network[i]) {
                    return false;
                }
            }
            if (remainder == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainder);
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }

        /** IPv4 becomes IPv4-mapped IPv6 so a v4 client and a v6-mapped client compare equal. */
        private static byte[] toComparable(byte[] address) {
            if (address.length == 16) {
                return address;
            }
            byte[] mapped = new byte[16];
            mapped[10] = (byte) 0xFF;
            mapped[11] = (byte) 0xFF;
            System.arraycopy(address, 0, mapped, 12, 4);
            return mapped;
        }
    }
}
