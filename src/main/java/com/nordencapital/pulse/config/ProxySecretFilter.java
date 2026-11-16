package com.nordencapital.pulse.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lets an {@code /api/**} request through only if its {@code X-Pulse-Proxy-Secret}
 * header matches the configured secret. Only the Vercel route handler knows the secret,
 * so the public API port answers nobody else. Registered for {@code /api/*} only (see
 * {@link WebConfig}), so the actuator health probes stay open for the container health
 * check. An empty secret turns the check off (local dev only).
 */
public class ProxySecretFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Pulse-Proxy-Secret";

	// Same shape as common.ApiError, written by hand because this runs before Spring MVC.
	private static final String FORBIDDEN_BODY = "{\"error\":\"forbidden\",\"message\":\"Access denied\"}";

	private final byte[] expectedHash;

	public ProxySecretFilter(String secret) {
		this.expectedHash = (secret == null || secret.isBlank()) ? null : sha256(secret);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (this.expectedHash == null || matches(request.getHeader(HEADER))) {
			chain.doFilter(request, response);
			return;
		}
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(FORBIDDEN_BODY);
	}

	private boolean matches(String given) {
		if (given == null) {
			return false;
		}
		// Compare fixed-length hashes in constant time, so neither the content nor the
		// length of the secret leaks through response timing.
		return MessageDigest.isEqual(this.expectedHash, sha256(given));
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

}
