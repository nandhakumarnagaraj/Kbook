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
 */
@Component
public class TerminalRequestFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(TerminalRequestFilter.class);

	private static final String TERMINAL_TOKEN_HEADER = "X-Terminal-Token";

	@Autowired
	private JwtUtility jwtUtility;

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
				TenantContext.setCurrentTerminalId(jwtUtility.extractTerminalId(terminalToken));
				TenantContext.setCurrentTerminalSeries(jwtUtility.extractTerminalSeries(terminalToken));
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
