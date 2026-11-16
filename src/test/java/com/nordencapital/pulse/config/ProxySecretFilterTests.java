package com.nordencapital.pulse.config;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ProxySecretFilterTests {

	@Test
	void emptySecretLetsEveryRequestThrough() throws Exception {
		MockHttpServletResponse response = run(new ProxySecretFilter(""), null);
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void configuredSecretBlocksAMissingHeader() throws Exception {
		MockHttpServletResponse response = run(new ProxySecretFilter("s3cret"), null);
		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentType()).startsWith("application/json");
	}

	@Test
	void configuredSecretAllowsTheMatchingHeader() throws Exception {
		MockHttpServletResponse response = run(new ProxySecretFilter("s3cret"), "s3cret");
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void clientIpIsTrustedOnlyAfterTheSecretMatched() throws Exception {
		assertThat(remoteAddr(new ProxySecretFilter("s3cret"), "s3cret", "203.0.113.7")).isEqualTo("203.0.113.7");
		assertThat(remoteAddr(new ProxySecretFilter("s3cret"), "s3cret", " 2001:db8::1 ")).isEqualTo("2001:db8::1");
		// Gate off (local dev): nobody proved to be the proxy, so the header is ignored.
		assertThat(remoteAddr(new ProxySecretFilter(""), null, "203.0.113.7")).isEqualTo("127.0.0.1");
	}

	@Test
	void clientIpMustBeAnIpLiteral() throws Exception {
		for (String bad : new String[] { "", "evil.example.com", "1.2.3.4, 5.6.7.8", "256.1.1.1", "1.2.3",
				"<script>", "a".repeat(46) + ":" }) {
			assertThat(remoteAddr(new ProxySecretFilter("s3cret"), "s3cret", bad)).as(bad).isEqualTo("127.0.0.1");
		}
		assertThat(ProxySecretFilter.isIpLiteral("::ffff:192.0.2.1")).isTrue();
		assertThat(ProxySecretFilter.isIpLiteral("10.0.0.255")).isTrue();
	}

	private static String remoteAddr(ProxySecretFilter filter, String secret, String clientIp) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ping");
		request.setRemoteAddr("127.0.0.1");
		if (secret != null) {
			request.addHeader(ProxySecretFilter.HEADER, secret);
		}
		request.addHeader(ProxySecretFilter.CLIENT_IP_HEADER, clientIp);
		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(request, new MockHttpServletResponse(), chain);
		return chain.getRequest().getRemoteAddr();
	}

	private static MockHttpServletResponse run(ProxySecretFilter filter, String header) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ping");
		if (header != null) {
			request.addHeader(ProxySecretFilter.HEADER, header);
		}
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());
		return response;
	}

}
