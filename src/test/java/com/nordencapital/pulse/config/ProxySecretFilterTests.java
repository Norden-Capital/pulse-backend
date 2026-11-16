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
