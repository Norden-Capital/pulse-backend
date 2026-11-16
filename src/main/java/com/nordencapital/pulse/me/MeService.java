package com.nordencapital.pulse.me;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nordencapital.pulse.auth.AuthLimiters;
import com.nordencapital.pulse.auth.AuthUser;
import com.nordencapital.pulse.auth.LoginEvents;
import com.nordencapital.pulse.auth.PasswordPolicy;
import com.nordencapital.pulse.auth.UserSessions;
import com.nordencapital.pulse.common.ApiException;
import com.nordencapital.pulse.user.Actors;
import com.nordencapital.pulse.user.User;

/**
 * The logged-in user's own data: profile, login history, password. Every method works
 * only on the caller's own user (from the session), never on an id from the request.
 */
@Service
public class MeService {

	private final Actors actors;

	private final SignInHistory signInHistory;

	private final LoginEvents loginEvents;

	private final PasswordEncoder passwordEncoder;

	private final UserSessions sessions;

	private final AuthLimiters limiters;

	private final Clock clock;

	public MeService(Actors actors, SignInHistory signInHistory, LoginEvents loginEvents,
			PasswordEncoder passwordEncoder, UserSessions sessions, AuthLimiters limiters, Clock clock) {
		this.actors = actors;
		this.signInHistory = signInHistory;
		this.loginEvents = loginEvents;
		this.passwordEncoder = passwordEncoder;
		this.sessions = sessions;
		this.limiters = limiters;
		this.clock = clock;
	}

	/**
	 * @param sessionLoginAt when this session logged in (null: then the login before the
	 * newest one)
	 */
	@Transactional(readOnly = true)
	public ProfileResponse profile(AuthUser principal, Instant sessionLoginAt) {
		User user = this.actors.active(principal);
		return ProfileResponse.from(user, this.loginEvents.previousSuccess(user.getId(), sessionLoginAt));
	}

	/**
	 * My login attempts per day.
	 * @param zone IANA zone id to cut days; null = UTC
	 * @param from first day; null = today minus 6 months
	 * @throws ApiException 400 {@code validation_error} if the zone is unknown
	 */
	@Transactional(readOnly = true)
	public List<SignInDay> signIns(AuthUser principal, String zone, LocalDate from) {
		ZoneId zoneId = parseZone(zone);
		User user = this.actors.active(principal);
		return this.signInHistory.days(user.getId(), zoneId, from);
	}

	/**
	 * Changes the password after checking the current one. On success, the user stays
	 * logged in on this session and is logged out everywhere else.
	 * @throws ApiException 400 {@code invalid_current_password}, 400
	 * {@code weak_password} or 429 {@code too_many_attempts} (after too many wrong
	 * current passwords)
	 */
	@Transactional
	public void changePassword(AuthUser principal, String currentPassword, String newPassword,
			String keepSessionId) {
		String limitKey = principal.id().toString();
		if (this.limiters.changePassword().isBlocked(limitKey)) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "too_many_attempts",
					"Too many attempts. Please wait and try again later");
		}
		User user = this.actors.active(principal);
		boolean currentOk = currentPassword != null && !PasswordPolicy.isTooLong(currentPassword)
				&& this.passwordEncoder.matches(currentPassword, user.getPasswordHash());
		if (!currentOk) {
			this.limiters.changePassword().record(limitKey);
			throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_current_password",
					"The current password is wrong");
		}
		PasswordPolicy.check(newPassword);
		user.setPasswordHash(this.passwordEncoder.encode(newPassword), this.clock.instant());
		this.limiters.changePassword().reset(limitKey);
		this.sessions.invalidateAllExcept(user.getId(), keepSessionId);
	}

	private static ZoneId parseZone(String zone) {
		if (zone == null || zone.isBlank()) {
			return ZoneOffset.UTC;
		}
		if (!ZoneId.getAvailableZoneIds().contains(zone)) {
			throw badZone();
		}
		try {
			return ZoneId.of(zone);
		}
		catch (DateTimeException ex) {
			throw badZone();
		}
	}

	private static ApiException badZone() {
		return new ApiException(HttpStatus.BAD_REQUEST, "validation_error",
				"zone: must be an IANA time zone id like Europe/Paris");
	}

}
