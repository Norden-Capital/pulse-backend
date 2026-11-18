package com.nordencapital.pulse.profile;

import java.time.Clock;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nordencapital.pulse.auth.AuthUser;
import com.nordencapital.pulse.common.ApiException;
import com.nordencapital.pulse.user.Actors;
import com.nordencapital.pulse.user.User;

/**
 * Stores the onboarding profile sent with register, and reads it back for the signed-in
 * user.
 */
@Service
public class ProfileService {

	private final UserProfileRepository profiles;

	private final Actors actors;

	private final Clock clock;

	public ProfileService(UserProfileRepository profiles, Actors actors, Clock clock) {
		this.profiles = profiles;
		this.actors = actors;
		this.clock = clock;
	}

	/**
	 * Creates or replaces the user's profile. The plan is recalculated here; only the
	 * explicit manual override is taken from the client. Called by register for a new or
	 * PENDING_VERIFICATION account only (never for an ACTIVE one).
	 */
	@Transactional
	public void saveFromRegister(User user, ProfileRequest request) {
		Instant now = this.clock.instant();
		Plan plan = EnergyFormula.calculate(request.answers());
		UserProfile profile = this.profiles.findById(user.getId()).orElseGet(() -> new UserProfile(user.getId(), now));
		profile.update(request.answers(), plan, request.targets(), now);
		this.profiles.save(profile);
	}

	/**
	 * @throws ApiException 404 {@code profile_not_found} if the user has no profile
	 */
	@Transactional(readOnly = true)
	public ProfileResponse mine(AuthUser principal) {
		User user = this.actors.active(principal);
		UserProfile profile = this.profiles.findById(user.getId())
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "profile_not_found", "No profile yet"));
		return ProfileResponse.from(profile, recalculate(profile));
	}

	/** The plan from the stored answers and formula version. */
	private static Plan recalculate(UserProfile profile) {
		if (!EnergyFormula.ID.equals(profile.getFormulaVersion())) {
			throw new IllegalStateException("Unknown formula version: " + profile.getFormulaVersion());
		}
		return EnergyFormula.calculate(profile.answers());
	}

}
