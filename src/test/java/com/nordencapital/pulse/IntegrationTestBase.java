package com.nordencapital.pulse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.nordencapital.pulse.auth.AuthLimiters;
import com.nordencapital.pulse.config.ProxySecretFilter;
import com.nordencapital.pulse.mail.MailMessage;
import com.nordencapital.pulse.mail.MailSender;
import com.nordencapital.pulse.profile.PlanLimiter;
import com.nordencapital.pulse.user.User;
import com.nordencapital.pulse.user.UserRepository;
import com.nordencapital.pulse.user.UserStatus;

/**
 * Full app + real Postgres (Testcontainers, needs Docker) with the proxy secret check on.
 * Emails are captured instead of sent, on the request thread. Each test starts with
 * empty users, tokens, sessions and limits. All subclasses share one Spring context and
 * one database container.
 */
@SpringBootTest(properties = { "app.proxy-secret=" + IntegrationTestBase.PROXY_SECRET, "app.mail.async=false" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MailCaptureConfiguration.class })
public abstract class IntegrationTestBase {

	protected static final String PROXY_SECRET = "test-proxy-secret";

	protected static final String PASSWORD = "correct-horse-battery";

	private static final Pattern TOKEN_IN_LINK = Pattern
		.compile("(?:verify-email|set-password)\\?token=([A-Za-z0-9_-]+)");

	@Autowired
	protected MockMvcTester mvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected UserRepository users;

	@Autowired
	protected PasswordEncoder passwordEncoder;

	@Autowired
	protected TransactionTemplate transaction;

	@Autowired
	protected AuthLimiters limiters;

	@Autowired
	protected CapturingMailSender mails;

	@Autowired
	protected PlanLimiter planLimiter;

	@BeforeEach
	void cleanDatabase() {
		this.jdbc.update("DELETE FROM spring_session");
		this.jdbc.update("DELETE FROM passkeys");
		this.jdbc.update("DELETE FROM user_profiles");
		this.jdbc.update("DELETE FROM auth_tokens");
		this.jdbc.update("DELETE FROM login_events");
		this.jdbc.update("DELETE FROM users");
		this.limiters.clearAll();
		this.planLimiter.clear();
		this.mails.clear();
	}

	protected User activeUser(String email) {
		return userWithStatus(email, UserStatus.ACTIVE);
	}

	protected User pendingUser(String email) {
		return userWithStatus(email, UserStatus.PENDING_VERIFICATION);
	}

	protected User disabledUser(String email) {
		return userWithStatus(email, UserStatus.DISABLED);
	}

	private User userWithStatus(String email, UserStatus status) {
		return this.transaction.execute((tx) -> {
			Instant now = Instant.now();
			User user = User.register(email, this.passwordEncoder.encode(PASSWORD), now);
			if (status == UserStatus.ACTIVE) {
				user.activate(now);
			}
			else {
				user.setStatus(status, now);
			}
			return this.users.save(user);
		});
	}

	protected User reload(UUID id) {
		return this.users.findById(id).orElseThrow();
	}

	protected int sessionCount(UUID userId) {
		return this.jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?", Integer.class,
				userId.toString());
	}

	/** Token from the link in the last email sent to this address. */
	protected String tokenFromLastMailTo(String email) {
		List<MailMessage> sent = this.mails.to(email);
		if (sent.isEmpty()) {
			throw new AssertionError("No email sent to " + email);
		}
		Matcher matcher = TOKEN_IN_LINK.matcher(sent.get(sent.size() - 1).html());
		if (!matcher.find()) {
			throw new AssertionError("No token link in email to " + email);
		}
		return matcher.group(1);
	}

	protected void expireAllTokens() {
		this.jdbc.update("UPDATE auth_tokens SET expires_at = now() - interval '1 minute'");
	}

	/** A browser behind the Vercel proxy (client IP not forwarded: 127.0.0.1). */
	protected Client client() {
		return new Client(this.mvc, null);
	}

	/** A browser behind the Vercel proxy that forwards this visitor IP in X-Client-IP. */
	protected Client client(String clientIp) {
		return new Client(this.mvc, clientIp);
	}

	protected Client loggedIn(String email) {
		Client client = client();
		MvcTestResult result = client.login(email, PASSWORD);
		if (result.getResponse().getStatus() != 200) {
			throw new AssertionError("Login failed for " + email + ": " + result.getResponse().getStatus());
		}
		return client;
	}

	protected static String json(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * A browser-like client as the Vercel route handler forwards it: keeps cookies, sends
	 * the XSRF-TOKEN cookie back as the X-XSRF-TOKEN header (like the frontend), and every
	 * request carries the proxy secret (like the route handler).
	 */
	public static final class Client {

		private final MockMvcTester mvc;

		private final String clientIp;

		private final Map<String, String> cookies = new LinkedHashMap<>();

		Client(MockMvcTester mvc, String clientIp) {
			this.mvc = mvc;
			this.clientIp = clientIp;
		}

		public MvcTestResult get(String uri) {
			return send(this.mvc.get().uri(uri));
		}

		public MvcTestResult post(String uri, String json) {
			return sendWithCsrf(this.mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json));
		}

		public MvcTestResult patch(String uri, String json) {
			return sendWithCsrf(this.mvc.patch().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json));
		}

		public MvcTestResult delete(String uri) {
			return sendWithCsrf(this.mvc.delete().uri(uri));
		}

		/** Same as {@link #post} but without the X-XSRF-TOKEN header. */
		public MvcTestResult postWithoutCsrf(String uri, String json) {
			return send(this.mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json));
		}

		/** Same as {@link #post} but with a made-up X-XSRF-TOKEN header. */
		public MvcTestResult postWithWrongCsrf(String uri, String json) {
			return send(this.mvc.post()
				.uri(uri)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json)
				.header("X-XSRF-TOKEN", "not-the-token"));
		}

		public MvcTestResult login(String email, String password) {
			return post("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
		}

		public MvcTestResult register(String email, String password) {
			return post("/api/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
		}

		/** Register with a {@code profile} object (raw JSON). */
		public MvcTestResult register(String email, String password, String profileJson) {
			return post("/api/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + password
					+ "\",\"profile\":" + profileJson + "}");
		}

		public String cookie(String name) {
			return this.cookies.get(name);
		}

		private MvcTestResult sendWithCsrf(MockMvcRequestBuilder request) {
			if (!this.cookies.containsKey("XSRF-TOKEN")) {
				get("/api/auth/csrf");
			}
			return send(request.header("X-XSRF-TOKEN", this.cookies.get("XSRF-TOKEN")));
		}

		private MvcTestResult send(MockMvcRequestBuilder request) {
			request.header(ProxySecretFilter.HEADER, PROXY_SECRET);
			if (this.clientIp != null) {
				request.header(ProxySecretFilter.CLIENT_IP_HEADER, this.clientIp);
			}
			if (!this.cookies.isEmpty()) {
				request.cookie(this.cookies.entrySet()
					.stream()
					.map((entry) -> new Cookie(entry.getKey(), entry.getValue()))
					.toArray(Cookie[]::new));
			}
			return keepCookies(request.exchange());
		}

		private MvcTestResult keepCookies(MvcTestResult result) {
			for (Cookie cookie : result.getResponse().getCookies()) {
				if (cookie.getMaxAge() == 0 || cookie.getValue() == null || cookie.getValue().isEmpty()) {
					this.cookies.remove(cookie.getName());
				}
				else {
					this.cookies.put(cookie.getName(), cookie.getValue());
				}
			}
			return result;
		}

	}

	public static final class CapturingMailSender implements MailSender {

		private final List<MailMessage> sent = Collections.synchronizedList(new ArrayList<>());

		@Override
		public void send(MailMessage message) {
			this.sent.add(message);
		}

		public List<MailMessage> all() {
			return List.copyOf(this.sent);
		}

		public List<MailMessage> to(String email) {
			return all().stream().filter((message) -> message.to().equals(email)).toList();
		}

		public void clear() {
			this.sent.clear();
		}

	}

}
