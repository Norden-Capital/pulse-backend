package com.nordencapital.pulse;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nordencapital.pulse.user.User;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The signed-in user's own profile, password and sign-in history.
 */
class MeIntegrationTests extends IntegrationTestBase {

	private static final String CHANGE = "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}";

	@Test
	void profileShowsMyAccount() {
		User user = activeUser("ana@example.com");
		Client client = loggedIn("ana@example.com");

		MvcTestResult profile = client.get("/api/me");
		assertThat(profile).hasStatus(HttpStatus.OK);
		assertThat(profile).bodyJson().extractingPath("$.id").isEqualTo(user.getId().toString());
		assertThat(profile).bodyJson().extractingPath("$.email").isEqualTo("ana@example.com");
		assertThat(profile).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
		assertThat(profile).bodyJson().extractingPath("$.createdAt").isNotNull();
		assertThat(profile).bodyJson().extractingPath("$.emailVerifiedAt").isNotNull();
		assertThat(profile).bodyJson().extractingPath("$.lastLoginAt").isNotNull();
		assertThat(profile).bodyJson().extractingPath("$.previousLoginAt").isNull();
		assertThat(json(profile)).doesNotContain("password");

		// A second sign-in: the first one becomes the previous login.
		Client again = loggedIn("ana@example.com");
		assertThat(again.get("/api/me")).bodyJson().extractingPath("$.previousLoginAt").isNotNull();
	}

	// ----- change password -----

	@Test
	void changePasswordKeepsThisSessionAndSignsOutTheOthers() {
		User user = activeUser("ana@example.com");
		Client phone = loggedIn("ana@example.com");
		Client laptop = loggedIn("ana@example.com");
		assertThat(sessionCount(user.getId())).isEqualTo(2);

		assertThat(phone.post("/api/me/password", CHANGE.formatted(PASSWORD, "a-brand-new-password")))
			.hasStatus(HttpStatus.NO_CONTENT);

		assertThat(phone.get("/api/me")).hasStatus(HttpStatus.OK);
		assertThat(laptop.get("/api/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(sessionCount(user.getId())).isEqualTo(1);
		assertThat(client().login("ana@example.com", PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(client().login("ana@example.com", "a-brand-new-password")).hasStatus(HttpStatus.OK);
	}

	@Test
	void changePasswordChecksTheCurrentOneAndTheRules() {
		activeUser("ana@example.com");
		Client client = loggedIn("ana@example.com");

		assertThat(client.post("/api/me/password", CHANGE.formatted("wrong-password-123", "a-brand-new-password")))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.error")
			.isEqualTo("invalid_current_password");
		assertThat(client.post("/api/me/password", CHANGE.formatted(PASSWORD, "short")))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.error")
			.isEqualTo("weak_password");
		assertThat(client().login("ana@example.com", PASSWORD)).hasStatus(HttpStatus.OK);
	}

	@Test
	void tooManyWrongCurrentPasswordsAreBlocked() {
		activeUser("ana@example.com");
		Client client = loggedIn("ana@example.com");
		for (int i = 0; i < 5; i++) {
			assertThat(client.post("/api/me/password", CHANGE.formatted("wrong-password-123", "a-brand-new-password")))
				.hasStatus(HttpStatus.BAD_REQUEST);
		}
		assertThat(client.post("/api/me/password", CHANGE.formatted(PASSWORD, "a-brand-new-password")))
			.hasStatus(HttpStatus.TOO_MANY_REQUESTS)
			.bodyJson()
			.extractingPath("$.error")
			.isEqualTo("too_many_attempts");
	}

	// ----- sign-in history -----

	@Test
	void signInsAreCountedPerDayInTheAskedZone() {
		User user = activeUser("ana@example.com");
		Client client = loggedIn("ana@example.com");
		this.jdbc.update("DELETE FROM login_events");
		// 23:30 UTC on Nov 1 is already Nov 2 in Paris (UTC+1 in November).
		event(user, "2026-11-01T10:00:00Z", true);
		event(user, "2026-11-01T23:30:00Z", true);
		event(user, "2026-11-01T23:45:00Z", false);
		event(user, "2026-11-03T12:00:00Z", false);

		assertThat(client.get("/api/me/sign-ins?from=2026-10-01&zone=UTC")).hasStatus(HttpStatus.OK)
			.bodyJson()
			.isStrictlyEqualTo("[{\"date\":\"2026-11-01\",\"ok\":2,\"failed\":1},"
					+ "{\"date\":\"2026-11-03\",\"ok\":0,\"failed\":1}]");
		assertThat(client.get("/api/me/sign-ins?from=2026-10-01&zone=Europe/Paris")).hasStatus(HttpStatus.OK)
			.bodyJson()
			.isStrictlyEqualTo("[{\"date\":\"2026-11-01\",\"ok\":1,\"failed\":0},"
					+ "{\"date\":\"2026-11-02\",\"ok\":1,\"failed\":1},"
					+ "{\"date\":\"2026-11-03\",\"ok\":0,\"failed\":1}]");
		// No zone = UTC.
		assertThat(client.get("/api/me/sign-ins?from=2026-10-01")).bodyJson()
			.extractingPath("$.length()")
			.isEqualTo(2);
		// "from" leaves out older days.
		assertThat(client.get("/api/me/sign-ins?from=2026-11-02&zone=UTC")).bodyJson()
			.isStrictlyEqualTo("[{\"date\":\"2026-11-03\",\"ok\":0,\"failed\":1}]");
	}

	@Test
	void unknownZoneIsRefused() {
		activeUser("ana@example.com");
		Client client = loggedIn("ana@example.com");
		assertThat(client.get("/api/me/sign-ins?zone=Mars/Olympus")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.error")
			.isEqualTo("validation_error");
		assertThat(client.get("/api/me/sign-ins?from=yesterday")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void signInsShowOnlyMyOwnAttempts() {
		activeUser("ana@example.com");
		activeUser("bob@example.com");
		client().login("bob@example.com", "wrong-password-123");
		Client ana = loggedIn("ana@example.com");
		assertThat(ana.get("/api/me/sign-ins")).bodyJson()
			.isStrictlyEqualTo("[{\"date\":\"" + java.time.LocalDate.now(java.time.ZoneOffset.UTC)
					+ "\",\"ok\":1,\"failed\":0}]");
	}

	private void event(User user, String at, boolean success) {
		this.jdbc.update("INSERT INTO login_events (id, user_id, created_at, success, ip) VALUES (?, ?, ?, ?, ?)",
				UUID.randomUUID(), user.getId(), Timestamp.from(Instant.parse(at)), success, "127.0.0.1");
	}

}
