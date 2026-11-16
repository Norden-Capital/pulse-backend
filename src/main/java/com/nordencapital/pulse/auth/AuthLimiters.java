package com.nordencapital.pulse.auth;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.nordencapital.pulse.config.AppProperties;

/**
 * In-memory limits (docs/api/auth.md §4):
 * <ul>
 * <li>login: after {@code login-max-failures} failed tries for one email + IP, blocked
 * until {@code login-lock-time} after the first failure (429);</li>
 * <li>passkey login: same rules, keyed by client IP (no email is typed);</li>
 * <li>change password: same rules, keyed by user id, for wrong current passwords;</li>
 * <li>register: at most {@code register-max-per-ip} per IP per window (429, keyed on IP
 * only so it says nothing about the email);</li>
 * <li>emails: forgot password and verification mails are limited per email + IP, and
 * every auth mail is also limited per email across all IPs, so nobody can flood one
 * inbox. Over these limits the request still gets 202 but nothing is sent.</li>
 * </ul>
 */
@Component
public class AuthLimiters {

	private final AttemptLimiter login;

	private final AttemptLimiter passkeyLogin;

	private final AttemptLimiter changePassword;

	private final AttemptLimiter register;

	private final AttemptLimiter forgotPassword;

	private final AttemptLimiter verifyMail;

	private final AttemptLimiter mailPerEmail;

	public AuthLimiters(AppProperties properties, Clock clock) {
		AppProperties.Auth auth = properties.auth();
		this.login = new AttemptLimiter(auth.loginMaxFailures(), auth.loginLockTime(), clock);
		this.passkeyLogin = new AttemptLimiter(auth.loginMaxFailures(), auth.loginLockTime(), clock);
		this.changePassword = new AttemptLimiter(auth.loginMaxFailures(), auth.loginLockTime(), clock);
		this.register = new AttemptLimiter(auth.registerMaxPerIp(), auth.registerWindow(), clock);
		this.forgotPassword = new AttemptLimiter(auth.forgotPasswordMaxRequests(), auth.forgotPasswordWindow(), clock);
		this.verifyMail = new AttemptLimiter(auth.verifyMailMaxRequests(), auth.verifyMailWindow(), clock);
		this.mailPerEmail = new AttemptLimiter(auth.mailPerEmailMax(), auth.mailPerEmailWindow(), clock);
	}

	public AttemptLimiter login() {
		return this.login;
	}

	public AttemptLimiter passkeyLogin() {
		return this.passkeyLogin;
	}

	public AttemptLimiter changePassword() {
		return this.changePassword;
	}

	public AttemptLimiter register() {
		return this.register;
	}

	/**
	 * Counts one forgot-password email if both the email + IP limit and the per-email
	 * limit allow it.
	 * @return false if no email may be sent (nothing is counted)
	 */
	public boolean tryResetMail(String email, String clientIp) {
		return tryMail(this.forgotPassword, email, clientIp);
	}

	/**
	 * Counts one verification email (register or resend) if both the email + IP limit and
	 * the per-email limit allow it.
	 * @return false if no email may be sent (nothing is counted)
	 */
	public boolean tryVerifyMail(String email, String clientIp) {
		return tryMail(this.verifyMail, email, clientIp);
	}

	/**
	 * Counts one "account exists" email. It shares the verification limit, since it is
	 * the answer to a register.
	 */
	public boolean tryAccountExistsMail(String email, String clientIp) {
		return tryMail(this.verifyMail, email, clientIp);
	}

	private boolean tryMail(AttemptLimiter kind, String email, String clientIp) {
		String key = email + "|" + clientIp;
		if (kind.isBlocked(key) || this.mailPerEmail.isBlocked(email)) {
			return false;
		}
		kind.record(key);
		this.mailPerEmail.record(email);
		return true;
	}

	/** Forgets all counters (used by tests). */
	public void clearAll() {
		this.login.clear();
		this.passkeyLogin.clear();
		this.changePassword.clear();
		this.register.clear();
		this.forgotPassword.clear();
		this.verifyMail.clear();
		this.mailPerEmail.clear();
	}

}
