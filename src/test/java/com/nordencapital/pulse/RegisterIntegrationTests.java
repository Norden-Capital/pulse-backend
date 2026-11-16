package com.nordencapital.pulse;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nordencapital.pulse.mail.MailMessage;
import com.nordencapital.pulse.user.User;
import com.nordencapital.pulse.user.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Register, verify email and resend verification (docs/api/auth.md).
 */
class RegisterIntegrationTests extends IntegrationTestBase {

	private static final String NEW_PASSWORD = "another-strong-password";

	// ----- register: the 4 cases -----

	@Test
	void unknownEmailCreatesAPendingAccountAndSendsAVerifyLink() {
		MvcTestResult result = client().register("  New@Example.com ", PASSWORD);
		assertThat(result).hasStatus(HttpStatus.ACCEPTED).bodyText().isEmpty();
		assertThat(result.getResponse().getHeaders("Set-Cookie")).noneMatch((c) -> c.startsWith("SESSION="));

		User user = this.users.findByEmail("new@example.com").orElseThrow();
		assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
		assertThat(user.getEmailVerifiedAt()).isNull();
		assertThat(user.getPasswordHash()).startsWith("{argon2@SpringSecurity_v5_8}");

		List<MailMessage> sent = this.mails.to("new@example.com");
		assertThat(sent).hasSize(1);
		assertThat(sent.get(0).subject()).isEqualTo("Confirm your email for Pulse");
		assertThat(sent.get(0).html()).contains("http://localhost:3000/verify-email?token=");
		assertThat(this.jdbc.queryForObject("SELECT purpose FROM auth_tokens WHERE user_id = ?", String.class,
				user.getId()))
			.isEqualTo("VERIFY");
	}

	@Test
	void pendingEmailTakesTheLatestPasswordAndGetsANewLink() {
		client().register("new@example.com", PASSWORD);
		String firstLink = tokenFromLastMailTo("new@example.com");

		assertThat(client().register("new@example.com", NEW_PASSWORD)).hasStatus(HttpStatus.ACCEPTED);
		String secondLink = tokenFromLastMailTo("new@example.com");
		assertThat(this.mails.to("new@example.com")).hasSize(2);
		assertThat(secondLink).isNotEqualTo(firstLink);

		// The older link is dead; the new one activates with the latest password.
		assertThat(client().post("/api/auth/verify-email", "{\"token\":\"" + firstLink + "\"}"))
			.hasStatus(HttpStatus.GONE);
		assertThat(client().post("/api/auth/verify-email", "{\"token\":\"" + secondLink + "\"}"))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(client().login("new@example.com", PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(client().login("new@example.com", NEW_PASSWORD)).hasStatus(HttpStatus.OK);
	}

	@Test
	void activeEmailKeepsItsPasswordAndGetsAnAccountExistsMail() {
		User user = activeUser("ana@example.com");
		String hashBefore = user.getPasswordHash();

		assertThat(client().register("ana@example.com", NEW_PASSWORD)).hasStatus(HttpStatus.ACCEPTED).bodyText().isEmpty();

		assertThat(reload(user.getId()).getPasswordHash()).isEqualTo(hashBefore);
		assertThat(reload(user.getId()).getStatus()).isEqualTo(UserStatus.ACTIVE);
		List<MailMessage> sent = this.mails.to("ana@example.com");
		assertThat(sent).hasSize(1);
		assertThat(sent.get(0).subject()).isEqualTo("You already have a Pulse account");
		assertThat(sent.get(0).html()).contains("href=\"http://localhost:3000/login\"")
			.contains("href=\"http://localhost:3000/forgot-password\"")
			.doesNotContain("token=");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM auth_tokens", Integer.class)).isZero();
		assertThat(client().login("ana@example.com", PASSWORD)).hasStatus(HttpStatus.OK);
	}

	@Test
	void disabledEmailGetsNothing() {
		User user = disabledUser("blocked@example.com");
		String hashBefore = user.getPasswordHash();

		assertThat(client().register("blocked@example.com", NEW_PASSWORD)).hasStatus(HttpStatus.ACCEPTED);
		assertThat(this.mails.all()).isEmpty();
		assertThat(reload(user.getId()).getPasswordHash()).isEqualTo(hashBefore);
		assertThat(reload(user.getId()).getStatus()).isEqualTo(UserStatus.DISABLED);
	}

	@Test
	void theAnswerIsTheSameWhateverTheEmail() {
		activeUser("active@example.com");
		pendingUser("pending@example.com");
		disabledUser("disabled@example.com");

		List<MvcTestResult> results = List.of(client().register("nobody@example.com", PASSWORD),
				client().register("pending@example.com", PASSWORD), client().register("active@example.com", PASSWORD),
				client().register("disabled@example.com", PASSWORD));
		for (MvcTestResult result : results) {
			assertThat(result).hasStatus(HttpStatus.ACCEPTED);
			assertThat(json(result)).isEmpty();
			assertThat(result.getResponse().getHeaders("Set-Cookie")).noneMatch((c) -> c.startsWith("SESSION="));
		}
	}

	@Test
	void registerChecksTheInput() {
		assertThat(client().register("new@example.com", "short")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.error")
			.isEqualTo("weak_password");
		assertThat(client().register("not-an-email", PASSWORD)).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.error")
			.isEqualTo("validation_error");
		assertThat(client().post("/api/auth/register", "{\"email\":\"new@example.com\"}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(this.users.findByEmail("new@example.com")).isEmpty();
		assertThat(this.mails.all()).isEmpty();
	}

	// ----- register limits -----

	@Test
	void tooManyRegistersFromOneIpAreBlocked() {
		Client client = client("198.51.100.4");
		for (int i = 0; i < 10; i++) {
			assertThat(client.register("user" + i + "@example.com", PASSWORD)).hasStatus(HttpStatus.ACCEPTED);
		}
		assertThat(client.register("user10@example.com", PASSWORD)).hasStatus(HttpStatus.TOO_MANY_REQUESTS)
			.bodyJson()
			.extractingPath("$.error")
			.isEqualTo("too_many_attempts");
		assertThat(this.users.findByEmail("user10@example.com")).isEmpty();
		// Another IP is not blocked.
		assertThat(client("198.51.100.5").register("user10@example.com", PASSWORD)).hasStatus(HttpStatus.ACCEPTED);
	}

	@Test
	void verificationMailsAreLimitedPerEmailAndIp() {
		Client client = client("198.51.100.4");
		for (int i = 0; i < 5; i++) {
			assertThat(client.register("new@example.com", PASSWORD)).hasStatus(HttpStatus.ACCEPTED);
		}
		assertThat(this.mails.to("new@example.com")).hasSize(3);
	}

	@Test
	void oneInboxCannotBeFloodedFromManyIps() {
		pendingUser("victim@example.com");
		for (int i = 1; i <= 8; i++) {
			assertThat(client("198.51.100." + i).post("/api/auth/resend-verification",
					"{\"email\":\"victim@example.com\"}"))
				.hasStatus(HttpStatus.ACCEPTED);
		}
		assertThat(this.mails.to("victim@example.com")).hasSize(5);
	}

	// ----- verify email -----

	@Test
	void verifyLinkActivatesTheAccountWithoutSigningIn() {
		client().register("new@example.com", PASSWORD);
		String token = tokenFromLastMailTo("new@example.com");

		assertThat(client().get("/api/auth/token/" + token)).hasStatus(HttpStatus.OK)
			.bodyJson()
			.isStrictlyEqualTo("{\"email\":\"new@example.com\",\"purpose\":\"VERIFY\"}");

		Client client = client();
		assertThat(client.post("/api/auth/verify-email", "{\"token\":\"" + token + "\"}"))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(client.cookie("SESSION")).isNull();
		assertThat(client.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);

		User user = this.users.findByEmail("new@example.com").orElseThrow();
		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(user.getEmailVerifiedAt()).isNotNull();
		assertThat(client().login("new@example.com", PASSWORD)).hasStatus(HttpStatus.OK);

		// One-time use.
		assertThat(client().post("/api/auth/verify-email", "{\"token\":\"" + token + "\"}"))
			.hasStatus(HttpStatus.GONE)
			.bodyJson()
			.extractingPath("$.error")
			.isEqualTo("token_invalid");
		assertThat(client().get("/api/auth/token/" + token)).hasStatus(HttpStatus.GONE);
	}

	@Test
	void expiredVerifyLinkDoesNothing() {
		client().register("new@example.com", PASSWORD);
		String token = tokenFromLastMailTo("new@example.com");
		expireAllTokens();
		assertThat(client().post("/api/auth/verify-email", "{\"token\":\"" + token + "\"}"))
			.hasStatus(HttpStatus.GONE);
		assertThat(this.users.findByEmail("new@example.com").orElseThrow().getStatus())
			.isEqualTo(UserStatus.PENDING_VERIFICATION);
	}

	@Test
	void verifyLinkIsUselessOnceTheAccountIsDisabled() {
		client().register("new@example.com", PASSWORD);
		String token = tokenFromLastMailTo("new@example.com");
		this.jdbc.update("UPDATE users SET status = 'DISABLED'");
		assertThat(client().post("/api/auth/verify-email", "{\"token\":\"" + token + "\"}"))
			.hasStatus(HttpStatus.GONE);
	}

	// ----- resend verification -----

	@Test
	void resendSendsANewLinkOnlyToPendingAccounts() {
		pendingUser("pending@example.com");
		activeUser("active@example.com");
		disabledUser("disabled@example.com");

		for (String email : List.of("pending@example.com", "active@example.com", "disabled@example.com",
				"nobody@example.com")) {
			assertThat(client().post("/api/auth/resend-verification", "{\"email\":\"" + email + "\"}"))
				.hasStatus(HttpStatus.ACCEPTED)
				.bodyText()
				.isEmpty();
		}
		assertThat(this.mails.all()).extracting(MailMessage::to).containsExactly("pending@example.com");
		assertThat(this.mails.all().get(0).subject()).isEqualTo("Confirm your email for Pulse");
	}

	@Test
	void resendMakesTheOlderLinkInvalid() {
		client().register("new@example.com", PASSWORD);
		String first = tokenFromLastMailTo("new@example.com");
		client().post("/api/auth/resend-verification", "{\"email\":\"new@example.com\"}");
		String second = tokenFromLastMailTo("new@example.com");

		assertThat(client().get("/api/auth/token/" + first)).hasStatus(HttpStatus.GONE);
		assertThat(client().post("/api/auth/verify-email", "{\"token\":\"" + second + "\"}"))
			.hasStatus(HttpStatus.NO_CONTENT);
		// The password from register is kept.
		assertThat(client().login("new@example.com", PASSWORD)).hasStatus(HttpStatus.OK);
	}

	@Test
	void resendIsLimitedButStillReturns202() {
		pendingUser("pending@example.com");
		Client client = client();
		for (int i = 0; i < 5; i++) {
			assertThat(client.post("/api/auth/resend-verification", "{\"email\":\"pending@example.com\"}"))
				.hasStatus(HttpStatus.ACCEPTED);
		}
		assertThat(this.mails.to("pending@example.com")).hasSize(3);
	}

}
