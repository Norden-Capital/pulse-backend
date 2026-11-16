package com.nordencapital.pulse;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With the prod cookie flag on (application-prod.yml), both cookies are Secure. The
 * browser sees the https Vercel origin, so this works although Vercel reaches the API
 * over plain http.
 */
@TestPropertySource(properties = "server.servlet.session.cookie.secure=true")
class SecureCookieIntegrationTests extends IntegrationTestBase {

	@Test
	void sessionAndCsrfCookiesAreSecure() {
		activeUser("ana@example.com");
		MvcTestResult login = client().login("ana@example.com", PASSWORD);
		assertThat(login).hasStatus(HttpStatus.OK);

		Cookie csrf = login.getResponse().getCookie("XSRF-TOKEN");
		assertThat(csrf).isNotNull();
		assertThat(csrf.getSecure()).isTrue();
		assertThat(csrf.getAttribute("SameSite")).isEqualTo("Lax");

		String session = login.getResponse()
			.getHeaders("Set-Cookie")
			.stream()
			.filter((c) -> c.startsWith("SESSION="))
			.findFirst()
			.orElseThrow();
		assertThat(session).contains("Secure").contains("HttpOnly").contains("SameSite=Lax").contains("Path=/");
	}

}
