package com.khanabook.saas.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.khanabook.saas.utility.JwtUtility;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Validates the terminal identity presented by a client via the X-Terminal-Token
 * header. A terminal token is issued by the /sync/terminal/activate endpoint and
 * binds a sync request to a specific registered, active terminal. This is the
 * trusted source of terminal identity (the client body fields terminalId /
 * terminalSeries are treated as untrusted and overwritten from this context).
 *
 * Also verifies credential_version: if the token's credVer is behind the DB value
 * (incremented on recovery/deactivation), the token is immediately rejected.
 */
@Component
public class TerminalRequestFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(TerminalRequestFilter.class);

	private static final String TERMINAL_TOKEN_HEADER = "X-Terminal-Token";

	@Autowired
	private JwtUtility jwtUtility;

	@Autowired
	private com.khanabook.saas.repository.RestaurantTerminalRepository terminalRepository;

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
			throws ServletException, IOException {

		String terminalToken = request.getHeader(TERMINAL_TOKEN_HEADER);

		if (terminalToken != null && !terminalToken.isBlank()) {
			try {
				if (!"terminal".equals(jwtUtility.extractTokenType(terminalToken))) {
					response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
							"Terminal token has an invalid type");
					return;
				}
				Long tokenRestaurantId = jwtUtility.extractRestaurantId(terminalToken);
				Long currentTenant = TenantContext.getCurrentTenant();
				if (tokenRestaurantId == null
						|| (currentTenant != null && !tokenRestaurantId.equals(currentTenant))) {
					response.sendError(HttpServletResponse.SC_FORBIDDEN,
							"Terminal token restaurant mismatch");
					return;
				}

				String terminalId = jwtUtility.extractTerminalId(terminalToken);
				String terminalSeries = jwtUtility.extractTerminalSeries(terminalToken);

				// Credential version check: reject tokens issued before a recovery/deactivation.
				// Legacy tokens (pre-credVer deployment) lack the credVer claim.
				// Safe transitional rule:
				//   credVer present → must match DB credential_version
				//   credVer absent + DB credential_version == 1 → accept (untouched legacy terminal)
				//   credVer absent + DB credential_version > 1 → reject (rotation occurred, legacy token is stale)
				//   terminal not ACTIVE → always reject
				Long tokenCredVer = jwtUtility.extractCredentialVersion(terminalToken);
				if (terminalId != null) {
					try {
						Long dbTerminalId = Long.parseLong(terminalId);
						var terminalOpt = terminalRepository.findById(dbTerminalId);
						if (terminalOpt.isPresent()) {
							var terminal = terminalOpt.get();

							// State check: non-active terminals are always rejected
							if (!"ACTIVE".equals(terminal.getStatus())) {
								log.warn("Terminal token rejected: terminal {} is {}", terminalId, terminal.getStatus());
								response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
										"Terminal has been deactivated");
								return;
							}

							long dbCredVer = terminal.getCredentialVersion() != null ? terminal.getCredentialVersion() : 1L;

							if (tokenCredVer != null) {
								// New token with credVer: must match DB credential_version exactly.
								// A token represents one specific credential generation.
								// Reject both older versions (revoked) and future versions (invalid/forged).
								if (!tokenCredVer.equals(dbCredVer)) {
									log.warn("Terminal token rejected: credVer mismatch terminal={} token={} db={}",
											terminalId, tokenCredVer, dbCredVer);
									response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
											"Terminal token credential version mismatch");
									return;
								}
							} else {
								// Legacy token without credVer: only accepted if terminal is still at version 1
								// (no rotation has ever occurred). Once any rotation happens (recovery,
								// deactivation, replacement), credential_version > 1 and legacy tokens
								// are immediately invalid.
								if (dbCredVer > 1L) {
									log.warn("Legacy terminal token rejected: terminal={} has credVer={} but token has no credVer claim",
											terminalId, dbCredVer);
									response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
											"Legacy terminal token has been superseded by credential rotation");
									return;
								}
							}
						}
					} catch (NumberFormatException e) {
						// terminalId might not be numeric for very old legacy tokens — skip check
						// This path should only exist for pre-migration tokens that will expire naturally
					}
				}

				TenantContext.setCurrentTerminalId(terminalId);
				TenantContext.setCurrentTerminalSeries(terminalSeries);
				TenantContext.setCurrentTerminalDevice(jwtUtility.extractDeviceId(terminalToken));
				TenantContext.setCurrentTerminalActive(Boolean.TRUE);
			} catch (Exception e) {
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid terminal token");
				return;
			}
		}

		filterChain.doFilter(request, response);
	}
}
