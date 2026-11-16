package com.nordencapital.pulse.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lets an {@code /api/**} request through only if its {@code X-Pulse-Proxy-Secret}
 * header matches the configured secret. Only the Vercel route handler knows the secret,
 * so the public API port answers nobody else. Registered for {@code /api/*} only (see
 * {@link WebConfig}), so the actuator health probes stay open for the container health
 * check. An empty secret turns the check off (local dev only).
 * <p>
 * Behind Vercel, the socket address is a Vercel server. The route handler sends the
 * visitor's IP in {@code X-Client-IP}. It is trusted only on a request whose secret
 * matched (so only the proxy can set it), and only if it is an IP literal. It then
 * becomes {@code request.getRemoteAddr()} for the rest of the app (rate limits, login
 * history).
 */
public class ProxySecretFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Pulse-Proxy-Secret";

	public static final String CLIENT_IP_HEADER = "X-Client-IP";

	private static final int MAX_IP_LENGTH = 45;

	private static final Pattern IPV4 = Pattern
		.compile("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

	/** Hex digits, colons and an optional embedded IPv4 tail; at least one colon. */
	private static final Pattern IPV6 = Pattern.compile("^(?=.*:)[0-9A-Fa-f:.]{2,45}$");

	// Same shape as common.ApiError, written by hand because this runs before Spring MVC.
	private static final String FORBIDDEN_BODY = "{\"error\":\"forbidden\",\"message\":\"Access denied\"}";

	private final byte[] expectedHash;

	public ProxySecretFilter(String secret) {
		this.expectedHash = (secret == null || secret.isBlank()) ? null : sha256(secret);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (this.expectedHash == null) {
			chain.doFilter(request, response);
			return;
		}
		if (matches(request.getHeader(HEADER))) {
			chain.doFilter(withClientIp(request), response);
			return;
		}
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(FORBIDDEN_BODY);
	}

	private static HttpServletRequest withClientIp(HttpServletRequest request) {
		String ip = request.getHeader(CLIENT_IP_HEADER);
		if (!isIpLiteral(ip)) {
			return request;
		}
		String clientIp = ip.strip();
		return new HttpServletRequestWrapper(request) {
			@Override
			public String getRemoteAddr() {
				return clientIp;
			}

			@Override
			public String getRemoteHost() {
				return clientIp;
			}
		};
	}

	static boolean isIpLiteral(String value) {
		if (value == null) {
			return false;
		}
		String ip = value.strip();
		return !ip.isEmpty() && ip.length() <= MAX_IP_LENGTH
				&& (IPV4.matcher(ip).matches() || IPV6.matcher(ip).matches());
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
