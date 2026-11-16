package com.nordencapital.pulse;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nordencapital.pulse.auth.AuthTokenService;
import com.nordencapital.pulse.auth.TokenPurpose;
import com.nordencapital.pulse.config.ProxySecretFilter;
import com.nordencapital.pulse.user.User;
import com.nordencapital.pulse.user.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Login, session, logout, CSRF, client IP, forgot / set password and token rules.
 */
class AuthFlowIntegrationTests extends IntegrationTestBase {

	@Autowired
	private AuthTokenService tokens;

	// ----- login -----

	@Test
	void loginStartsServerSideSession() {
		User user = activeUser("ana@example.com");
		Client client = client();

		MvcTestResult login = client.login("ana@example.com", PASSWORD);
		assertThat(login).hasStatus(HttpStatus.OK)
			.bodyJson()
			.isStrictlyEqualTo("{\"id\":\"" + user.getId() + "\",\"email\":\"ana@example.com\"}");

		String setCookie = String.join("\n", login.getResponse().getHeaders("Set-Cookie"));
		assertThat(setCookie).contains("SESSION=").contains("HttpOnly").contains("SameSite=Lax").contains("Path=/");
		assertThat(setCookie).contains("XSRF-TOKEN=");
		assertThat(sessionCount(user.getId())).isEqualTo(1);
		assertThat(reload(user.getId()).getLastLoginAt()).isNotNull();

		assertThat(client.get("/api/auth/me")).hasStatus(HttpStatus.OK)
			.bodyJson()
			.extractingPath("$.email")
			.isEqualTo("ana@example.com");
	}

	@Test
	void sessionSurvivesAcrossRequests() {
		User user = activeUser("ana@example.com");
		Client client = loggedIn("ana@example.com");
		String session = client.cookie("SESSION");
		for (int i = 0; i < 3; i++) {
			assertThat(client.get("/api/auth/me")).hasStatus(HttpStatus.OK);
			assertThat(client.get("/api/me")).hasStatus(HttpStatus.OK);
		}
		assertThat(client.cookie("SESSION")).isEqualTo(session);
		assertThat(sessionCount(user.getId())).isEqualTo(1);
	}

	@Test
	void loginChangesTheSessionIdAndTheCsrfToken() {
		activeUser("ana@example.com");
		Client client = client();
		client.post("/api/auth/passkey/options", ""); // makes an anonymous session
		String sessionBefore = client.cookie("SESSION");
		String csrfBefore = client.cookie("XSRF-TOKEN");
		assertThat(sessionBefore).isNotNull();

		assertThat(client.login("ana@example.com", PASSWORD)).hasStatus(HttpStatus.OK);
		assertThat(client.cookie("SESSION")).isNotNull().isNotEqualTo(sessionBefore);
		assertThat(client.cookie("XSRF-TOKEN")).isNotNull().isNotEqualTo(csrfBefore);
	}

	@Test
	void anonymousSessionFromPasskeyOptionsIsNotSignedIn() {
		Client client = client();
		assertThat(client.post("/api/auth/passkey/options", "")).hasStatus(HttpStatus.OK);
		assertThat(client.cookie("SESSION")).isNotNull();
		for (String uri : List.of("/api/auth/me", "/api/me")) {
			assertThat(client.get(uri)).as(uri)
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson()
				.isStrictlyEqualTo("{\"error\":\"unauthorized\",\"message\":\"Please log in\"}");
		}
	}

	@Test
	void loginIgnoresEmailCaseAndSpaces() {
		activeUser("ana@example.com");
		assertThat(client().login("  Ana@Example.COM ", PASSWORD)).hasStatus(HttpStatus.OK);
	}

	@Test
	void loginErrorIsTheSameForUnknownWrongPasswordAndDisabled() {
		activeUser("active@example.com");
		pendingUser("pending@example.com");
		disabledUser("disabled@example.com");

		List<MvcTestResult> results = List.of(client().login("active@example.com", "wrong-password-123"),
				client().login("nobody@example.com", PASSWORD), client().login("disabled@example.com", PASSWORD),
				client().login("pending@example.com", "wrong-password-123"));
		for (MvcTestResult result : results) {
			assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson()
				.isStrictlyEqualTo("{\"error\":\"invalid_credentials\",\"message\":\"Invalid email or password\"}");
			assertThat(result.getResponse().getHeaders("Set-Cookie")).noneMatch((c) -> c.startsWith("SESSION="));
		}
	}

	@Test
	void unverifiedAccountWithTheRightPasswordIsToldToConfirmTheEmail() {
		User user = pendingUser("pending@example.com");
		Client client = client();
		MvcTestResult result = client.login("pending@example.com", PASSWORD);
		assertThat(result).hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson()
			.extractingPath("$.error")
			.isEqualTo("email_not_verified");
		assertThat(client.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(sessionCount(user.getId())).isZero();
	}

	@Test
	void loginIsBlockedAfterFiveFailures() {
		activeUser("ana@example.com");
		Client client = client();
		for (int i = 0; i < 5; i++) {
			assertThat(client.login("ana@example.com", "wrong-password-123")).hasStatus(HttpStatus.UNAUTHORIZED);
		}
		// Even the right password is refused while blocked.
		assertThat(client.login("ana@example.com", PASSWORD)).hasStatus(HttpStatus.TOO_MANY_REQUESTS)
			.bodyJson()
			.extractingPath("$.error")
			.isEqualTo("too_many_attempts");
		// Another visitor IP (forwarded by the proxy) is counted apart.
		assertThat(client("203.0.113.9").login("ana@example.com", PASSWORD)).hasStatus(HttpStatus.OK);
	}

	@Test
	void loginHistoryKeepsTheForwardedVisitorIp() {
		User user = activeUser("ana@example.com");
		client("203.0.113.7").login("ana@example.com", PASSWORD);
		client("2001:db8::1").login("ana@example.com", "wrong-password-123");
		client("not-an-ip").login("ana@example.com", PASSWORD);

		assertThat(this.jdbc.queryForList("SELECT ip FROM login_events WHERE user_id = ? ORDER BY created_at",
				String.class, user.getId()))
			.containsExactly("203.0.113.7", "2001:db8::1", "127.0.0.1");
	}

	@Test
	void clientIpHeaderIsNotTrustedWithoutTheProxySecret() {
		activeUser("ana@example.com");
		assertThat(this.mvc.get().uri("/api/auth/csrf").header(ProxySecretFilter.CLIENT_IP_HEADER, "203.0.113.7"))
			.hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson()
			.extractingPath("$.error")
			.isEqualTo("forbidden");
	}

	// ----- logout -----

	@Test
	void logoutDeletesSession() {
		User user = activeUser("ana@example.com");
		Client client = loggedIn("ana@example.com");
		MvcTestResult logout = client.post("/api/auth/logout", "");
		assertThat(logout).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(String.join("\n", logout.getResponse().getHeaders("Set-Cookie"))).contains("XSRF-TOKEN=;");
		assertThat(client.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(sessionCount(user.getId())).isZero();
	}

	@Test
	void stolenOldSessionCookieStopsWorkingAfterLogout() {
		activeUser("ana@example.com");
		Client client = loggedIn("ana@example.com");
		String sessionCookie = client.cookie("SESSION");
		client.post("/api/auth/logout", "");

		assertThat(this.mvc.get()
			.uri("/api/auth/me")
			.header(ProxySecretFilter.HEADER, PROXY_SECRET)
			.cookie(new jakarta.servlet.http.Cookie("SESSION", sessionCookie))).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void logoutWorksWithoutASession() {
		assertThat(client().post("/api/auth/logout", "")).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void disabledUserLosesAnOpenSession() {
		User user = activeUser("ana@example.com");
		Client client = loggedIn("ana@example.com");
		this.jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", user.getId());
		assertThat(client.get("/api/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(client.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// ----- CSRF and access rules -----

	@Test
	void everyWriteNeedsTheCsrfToken() {
		activeUser("ana@example.com");
		String login = "{\"email\":\"ana@example.com\",\"password\":\"" + PASSWORD + "\"}";
		for (String uri : List.of("/api/auth/login", "/api/auth/register", "/api/auth/forgot-password",
				"/api/auth/resend-verification", "/api/auth/verify-email", "/api/auth/set-password",
				"/api/auth/logout")) {
			assertThat(client().postWithoutCsrf(uri, login)).as(uri)
				.hasStatus(HttpStatus.FORBIDDEN)
				.bodyJson()
				.isStrictlyEqualTo("{\"error\":\"csrf_invalid\",\"message\":\"Missing or invalid CSRF token\"}");
		}
		Client client = client();
		client.get("/api/auth/csrf");
		assertThat(client.postWithWrongCsrf("/api/auth/login", login)).hasStatus(HttpStatus.FORBIDDEN);

		Client signedIn = loggedIn("ana@example.com");
		assertThat(signedIn.postWithoutCsrf("/api/me/password",
				"{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"a-brand-new-password\"}"))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(this.mails.all()).isEmpty();
	}

	@Test
	void csrfEndpointSetsAReadableCookie() {
		MvcTestResult result = client().get("/api/auth/csrf");
		assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
		jakarta.servlet.http.Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
		assertThat(cookie).isNotNull();
		assertThat(cookie.isHttpOnly()).isFalse();
		assertThat(cookie.getPath()).isEqualTo("/");
		// MockMvc leaves SameSite out of the Set-Cookie text; the cookie carries it.
		assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
		assertThat(cookie.getSecure()).isFalse(); // local profile; prod sets Secure
	}

	@Test
	void privateEndpointsNeedASession() {
		for (String uri : List.of("/api/auth/me", "/api/me", "/api/me/sign-ins", "/api/me/passkeys",
				"/api/anything-else")) {
			assertThat(client().get(uri)).as(uri)
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson()
				.isStrictlyEqualTo("{\"error\":\"unauthorized\",\"message\":\"Please log in\"}");
		}
	}

	// ----- forgot password -----

	@Test
	void forgotPasswordAlwaysReturns202AndMailsOnlyActiveAndPendingUsers() {
		activeUser("active@example.com");
		pendingUser("pending@example.com");
		disabledUser("disabled@example.com");

		for (String email : List.of("active@example.com", "pending@example.com", "disabled@example.com",
				"nobody@example.com")) {
			assertThat(client().post("/api/auth/forgot-password", "{\"email\":\"" + email + "\"}"))
				.hasStatus(HttpStatus.ACCEPTED)
				.bodyText()
				.isEmpty();
		}
		assertThat(this.mails.all()).extracting((m) -> m.to())
			.containsExactlyInAnyOrder("active@example.com", "pending@example.com");
		assertThat(this.mails.all()).allSatisfy((m) -> assertThat(m.subject()).isEqualTo("Reset your Pulse password"));
	}

	@Test
	void forgotPasswordIsLimitedButStillReturns202() {
		activeUser("ana@example.com");
		Client client = client();
		for (int i = 0; i < 5; i++) {
			assertThat(client.post("/api/auth/forgot-password", "{\"email\":\"ana@example.com\"}"))
				.hasStatus(HttpStatus.ACCEPTED);
		}
		assertThat(this.mails.to("ana@example.com")).hasSize(3);
	}

	@Test
	void resetPasswordFlow() {
		User user = activeUser("ana@example.com");
		client().post("/api/auth/forgot-password", "{\"email\":\"ana@example.com\"}");
		String token = tokenFromLastMailTo("ana@example.com");
		assertThat(this.mails.to("ana@example.com").get(0).html()).contains("/set-password?token=" + token);

		assertThat(client().get("/api/auth/token/" + token)).hasStatus(HttpStatus.OK)
			.bodyJson()
			.isStrictlyEqualTo("{\"email\":\"ana@example.com\",\"purpose\":\"RESET\"}");

		Client client = client();
		assertThat(client.post("/api/auth/set-password",
				"{\"token\":\"" + token + "\",\"password\":\"a-brand-new-password\"}"))
			.hasStatus(HttpStatus.NO_CONTENT);
		// Does not log in.
		assertThat(client.cookie("SESSION")).isNull();

		assertThat(client().login("ana@example.com", PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(client().login("ana@example.com", "a-brand-new-password")).hasStatus(HttpStatus.OK);
		assertThat(reload(user.getId()).getPasswordHash()).doesNotContain("a-brand-new-password");

		// One-time use.
		assertThat(client().get("/api/auth/token/" + token)).hasStatus(HttpStatus.GONE);
		assertThat(client().post("/api/auth/set-password",
				"{\"token\":\"" + token + "\",\"password\":\"another-new-password\"}"))
			.hasStatus(HttpStatus.GONE)
			.bodyJson()
			.extractingPath("$.error")
			.isEqualTo("token_invalid");
	}

	@Test
	void resetActivatesAPendingAccount() {
		User user = pendingUser("pending@example.com");
		client().post("/api/auth/forgot-password", "{\"email\":\"pending@example.com\"}");
		String token = tokenFromLastMailTo("pending@example.com");

		assertThat(client().post("/api/auth/set-password",
				"{\"token\":\"" + token + "\",\"password\":\"a-brand-new-password\"}"))
			.hasStatus(HttpStatus.NO_CONTENT);
		User after = reload(user.getId());
		assertThat(after.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(after.getEmailVerifiedAt()).isNotNull();
		assertThat(client().login("pending@example.com", "a-brand-new-password")).hasStatus(HttpStatus.OK);
	}

	@Test
	void passwordResetSignsOutEverywhere() {
		User user = activeUser("ana@example.com");
		Client phone = loggedIn("ana@example.com");
		Client laptop = loggedIn("ana@example.com");
		assertThat(sessionCount(user.getId())).isEqualTo(2);
		String token = this.tokens.issue(user, TokenPurpose.RESET);

		client().post("/api/auth/set-password", "{\"token\":\"" + token + "\",\"password\":\"a-brand-new-password\"}");

		assertThat(phone.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(laptop.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(sessionCount(user.getId())).isZero();
	}

	@Test
	void weakPasswordKeepsTheTokenUsable() {
		User user = activeUser("ana@example.com");
		String token = this.tokens.issue(user, TokenPurpose.RESET);
		assertThat(client().post("/api/auth/set-password", "{\"token\":\"" + token + "\",\"password\":\"short\"}"))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.error")
			.isEqualTo("weak_password");
		assertThat(client().get("/api/auth/token/" + token)).hasStatus(HttpStatus.OK);
	}

	// ----- tokens -----

	@Test
	void tokensOnlyWorkForTheirOwnPurpose() {
		User pending = pendingUser("pending@example.com");
		String verify = this.tokens.issue(pending, TokenPurpose.VERIFY);
		assertThat(client().post("/api/auth/set-password",
				"{\"token\":\"" + verify + "\",\"password\":\"a-brand-new-password\"}"))
			.hasStatus(HttpStatus.GONE);

		String reset = this.tokens.issue(pending, TokenPurpose.RESET);
		assertThat(client().post("/api/auth/verify-email", "{\"token\":\"" + reset + "\"}"))
			.hasStatus(HttpStatus.GONE);
		assertThat(reload(pending.getId()).getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
	}

	@Test
	void expiredTokenIsInvalid() {
		User user = activeUser("ana@example.com");
		String token = this.tokens.issue(user, TokenPurpose.RESET);
		expireAllTokens();

		assertThat(client().get("/api/auth/token/" + token)).hasStatus(HttpStatus.GONE);
		assertThat(client().post("/api/auth/set-password",
				"{\"token\":\"" + token + "\",\"password\":\"a-brand-new-password\"}"))
			.hasStatus(HttpStatus.GONE);
	}

	@Test
	void newTokenMakesOlderOneInvalid() {
		User user = activeUser("ana@example.com");
		String first = this.tokens.issue(user, TokenPurpose.RESET);
		String second = this.tokens.issue(user, TokenPurpose.RESET);

		assertThat(client().get("/api/auth/token/" + first)).hasStatus(HttpStatus.GONE);
		assertThat(client().get("/api/auth/token/" + second)).hasStatus(HttpStatus.OK);
	}

	@Test
	void tokenOnlyStoredAsHash() {
		User user = activeUser("ana@example.com");
		String token = this.tokens.issue(user, TokenPurpose.RESET);
		String stored = this.jdbc.queryForObject("SELECT token_hash FROM auth_tokens WHERE user_id = ?", String.class,
				user.getId());
		assertThat(stored).hasSize(64).isNotEqualTo(token).doesNotContain(token);
	}

	@Test
	void unknownTokenIsInvalid() {
		assertThat(client().get("/api/auth/token/not-a-real-token")).hasStatus(HttpStatus.GONE)
			.bodyJson()
			.isStrictlyEqualTo("{\"error\":\"token_invalid\",\"message\":\"This link is invalid or has expired\"}");
		assertThat(client().get("/api/auth/token/" + "x".repeat(200))).hasStatus(HttpStatus.GONE);
	}

	@Test
	void resetTokenStopsWorkingWhenUserIsDisabled() {
		User user = activeUser("ana@example.com");
		String token = this.tokens.issue(user, TokenPurpose.RESET);
		this.jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", user.getId());
		assertThat(client().get("/api/auth/token/" + token)).hasStatus(HttpStatus.GONE);
	}

}
