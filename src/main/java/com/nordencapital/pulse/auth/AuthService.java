package com.nordencapital.pulse.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nordencapital.pulse.common.ApiException;
import com.nordencapital.pulse.common.Emails;
import com.nordencapital.pulse.mail.AuthMails;
import com.nordencapital.pulse.profile.ProfileRequest;
import com.nordencapital.pulse.profile.ProfileService;
import com.nordencapital.pulse.user.User;
import com.nordencapital.pulse.user.UserRepository;
import com.nordencapital.pulse.user.UserStatus;

/**
 * Register, email verification, login, forgot password and set password.
 * <p>
 * Register, resend verification and forgot password never tell whether an email has an
 * account: they always return quietly, and the difference only shows in the inbox
 * (docs/api/auth.md §3).
 */
@Service
public class AuthService {

	private final UserRepository users;

	private final AuthTokenService tokens;

	private final PasswordEncoder passwordEncoder;

	private final AuthMails mails;

	private final UserSessions sessions;

	private final AuthLimiters limiters;

	private final LoginEvents loginEvents;

	private final ProfileService profiles;

	private final Clock clock;

	/**
	 * Checked when the email is unknown or cannot log in, so every failed login takes
	 * about the same time (no user enumeration by timing).
	 */
	private final String dummyHash;

	public AuthService(UserRepository users, AuthTokenService tokens, PasswordEncoder passwordEncoder,
			AuthMails mails, UserSessions sessions, AuthLimiters limiters, LoginEvents loginEvents, ProfileService profiles,
			Clock clock) {
		this.users = users;
		this.tokens = tokens;
		this.passwordEncoder = passwordEncoder;
		this.mails = mails;
		this.sessions = sessions;
		this.limiters = limiters;
		this.loginEvents = loginEvents;
		this.profiles = profiles;
		this.clock = clock;
		this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	/**
	 * Register. Always returns quietly whatever the email's state:
	 * <ul>
	 * <li>unknown: new PENDING_VERIFICATION user, Verify email;</li>
	 * <li>PENDING_VERIFICATION: the latest register wins (new password, older links
	 * invalid), new Verify email;</li>
	 * <li>ACTIVE: nothing changes, "account exists" email;</li>
	 * <li>DISABLED: nothing.</li>
	 * </ul>
	 * The optional onboarding profile is stored with a new account, replaces the profile of
	 * a pending one (kept when this register has none), and is discarded for ACTIVE and
	 * DISABLED accounts (never overwrite a real account).
	 * <p>
	 * Over the email limits, nothing changes and nothing is sent. The password is hashed
	 * in every case, so the response time does not depend on the branch.
	 * @throws ApiException 400 {@code weak_password} or 429 {@code too_many_attempts}
	 * (too many registers from this IP)
	 */
	@Transactional
	public void register(String email, String password, ProfileRequest profile, String clientIp) {
		PasswordPolicy.check(password);
		if (!this.limiters.register().tryAcquire(clientIp)) {
			throw tooManyAttempts();
		}
		String normalized = Emails.normalize(email);
		String hash = this.passwordEncoder.encode(password);
		Instant now = this.clock.instant();
		Optional<User> found = this.users.findByEmail(normalized);
		if (found.isEmpty()) {
			if (this.limiters.tryVerifyMail(normalized, clientIp)) {
				User user = this.users.saveAndFlush(User.register(normalized, hash, now));
				if (profile != null) {
					this.profiles.saveFromRegister(user, profile);
				}
				this.mails.sendVerify(user.getEmail(), this.tokens.issue(user, TokenPurpose.VERIFY));
			}
			return;
		}
		User user = found.get();
		switch (user.getStatus()) {
			case PENDING_VERIFICATION -> {
				if (this.limiters.tryVerifyMail(normalized, clientIp)) {
					user.setPasswordHash(hash, now);
					if (profile != null) {
						this.profiles.saveFromRegister(user, profile);
					}
					this.mails.sendVerify(user.getEmail(), this.tokens.issue(user, TokenPurpose.VERIFY));
				}
			}
			case ACTIVE -> {
				if (this.limiters.tryAccountExistsMail(normalized, clientIp)) {
					this.mails.sendAccountExists(user.getEmail());
				}
			}
			case DISABLED -> {
				// Nothing.
			}
		}
	}

	/**
	 * Sends a new Verify email if the account is PENDING_VERIFICATION and the limits
	 * allow it. Always returns quietly.
	 */
	@Transactional
	public void resendVerification(String email, String clientIp) {
		String normalized = Emails.normalize(email);
		this.users.findByEmail(normalized)
			.filter((user) -> user.getStatus() == UserStatus.PENDING_VERIFICATION)
			.filter((user) -> this.limiters.tryVerifyMail(normalized, clientIp))
			.ifPresent((user) -> this.mails.sendVerify(user.getEmail(), this.tokens.issue(user, TokenPurpose.VERIFY)));
	}

	/**
	 * Confirms the email with a VERIFY token: the user becomes ACTIVE. Does not log in.
	 * @throws ApiException 410 {@code token_invalid}
	 */
	@Transactional
	public void verifyEmail(String rawToken) {
		AuthToken token = this.tokens.findValid(rawToken, TokenPurpose.VERIFY).orElseThrow(AuthService::tokenInvalid);
		User user = token.getUser();
		user.activate(this.clock.instant());
		this.tokens.deleteAll(user);
	}

	/**
	 * Checks email and password.
	 * <ul>
	 * <li>unknown email, wrong password, DISABLED: 401 {@code invalid_credentials}, same
	 * answer and about the same time;</li>
	 * <li>right password but PENDING_VERIFICATION: 403 {@code email_not_verified} (only
	 * someone who knows the password learns this).</li>
	 * </ul>
	 * Every attempt of a known user is saved in the login history (success or not).
	 * Unknown emails and attempts blocked by the limiter (429) are not saved. The
	 * transaction is not rolled back by the error, so failed attempts stay saved.
	 * @return the logged-in user
	 * @throws ApiException 401 {@code invalid_credentials}, 403
	 * {@code email_not_verified} or 429 {@code too_many_attempts}
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public User login(String email, String password, String clientIp) {
		String normalized = Emails.normalize(email);
		String limitKey = normalized + "|" + clientIp;
		if (this.limiters.login().isBlocked(limitKey)) {
			throw tooManyAttempts();
		}
		Optional<User> found = this.users.findByEmail(normalized);
		boolean canTry = found.map((user) -> user.getStatus() != UserStatus.DISABLED).orElse(false);
		String hash = canTry ? found.get().getPasswordHash() : this.dummyHash;
		boolean passwordOk = !PasswordPolicy.isTooLong(password) && this.passwordEncoder.matches(password, hash);
		if (!canTry || !passwordOk) {
			this.limiters.login().record(limitKey);
			found.ifPresent((user) -> this.loginEvents.record(user.getId(), false, clientIp, this.clock.instant()));
			throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Invalid email or password");
		}
		User user = found.get();
		if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
			this.loginEvents.record(user.getId(), false, clientIp, this.clock.instant());
			throw new ApiException(HttpStatus.FORBIDDEN, "email_not_verified",
					"Please confirm your email first. Check your inbox for the link");
		}
		this.limiters.login().reset(limitKey);
		user.recordLogin(this.clock.instant());
		this.loginEvents.record(user.getId(), true, clientIp, this.clock.instant());
		return user;
	}

	/**
	 * Sends a reset link if the account is ACTIVE or PENDING_VERIFICATION and the limits
	 * allow it. Always returns quietly, so the caller cannot tell whether the email
	 * exists.
	 */
	@Transactional
	public void forgotPassword(String email, String clientIp) {
		String normalized = Emails.normalize(email);
		this.users.findByEmail(normalized)
			.filter((user) -> user.getStatus() == UserStatus.ACTIVE
					|| user.getStatus() == UserStatus.PENDING_VERIFICATION)
			.filter((user) -> this.limiters.tryResetMail(normalized, clientIp))
			.ifPresent((user) -> this.mails.sendPasswordReset(user.getEmail(),
					this.tokens.issue(user, TokenPurpose.RESET)));
	}

	/**
	 * @throws ApiException 410 {@code token_invalid}
	 */
	@Transactional(readOnly = true)
	public TokenInfo checkToken(String rawToken) {
		AuthToken token = this.tokens.findValid(rawToken).orElseThrow(AuthService::tokenInvalid);
		return new TokenInfo(token.getUser().getEmail(), token.getPurpose());
	}

	/**
	 * Sets a new password with a RESET token. The user becomes ACTIVE (a pending account
	 * is verified too: the link proves the inbox) and is logged out everywhere. Does not
	 * log in.
	 * @throws ApiException 410 {@code token_invalid} or 400 {@code weak_password}
	 */
	@Transactional
	public void setPassword(String rawToken, String password) {
		AuthToken token = this.tokens.findValid(rawToken, TokenPurpose.RESET).orElseThrow(AuthService::tokenInvalid);
		PasswordPolicy.check(password);
		User user = token.getUser();
		Instant now = this.clock.instant();
		user.setPasswordHash(this.passwordEncoder.encode(password), now);
		user.activate(now);
		this.tokens.deleteAll(user);
		this.sessions.invalidateAll(user.getId());
	}

	/**
	 * Loads the logged-in user again, so a stale session cannot outlive a disabled user.
	 * @return empty if the user is gone or not ACTIVE any more
	 */
	@Transactional(readOnly = true)
	public Optional<User> current(AuthUser principal) {
		return this.users.findById(principal.id()).filter((user) -> user.getStatus() == UserStatus.ACTIVE);
	}

	private static ApiException tokenInvalid() {
		return new ApiException(HttpStatus.GONE, "token_invalid", "This link is invalid or has expired");
	}

	private static ApiException tooManyAttempts() {
		return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "too_many_attempts",
				"Too many attempts. Please wait and try again later");
	}

	public record TokenInfo(String email, TokenPurpose purpose) {
	}

}
