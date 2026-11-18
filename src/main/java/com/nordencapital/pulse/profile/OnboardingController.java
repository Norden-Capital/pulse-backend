package com.nordencapital.pulse.profile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.nordencapital.pulse.auth.AuthUser;
import com.nordencapital.pulse.common.ApiException;

/**
 * Onboarding plan (public, no account) and the signed-in user's profile. Contract:
 * docs/api/onboarding-profile.md.
 */
@RestController
public class OnboardingController {

	private final ProfileService profiles;

	private final PlanLimiter planLimiter;

	public OnboardingController(ProfileService profiles, PlanLimiter planLimiter) {
		this.profiles = profiles;
		this.planLimiter = planLimiter;
	}

	/**
	 * Public, CSRF as every POST. Calculates the plan from the answers; stores nothing and
	 * starts no session.
	 * @throws ApiException 429 {@code too_many_attempts} (too many from this IP)
	 */
	@PostMapping("/api/onboarding/plan")
	public Plan plan(@Valid @RequestBody ProfileAnswers answers, HttpServletRequest request) {
		if (!this.planLimiter.tryAcquire(request.getRemoteAddr())) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "too_many_attempts",
					"Too many attempts. Please wait and try again later");
		}
		return EnergyFormula.calculate(answers);
	}

	/** My answers, targets and provenance (404 profile_not_found if none). */
	@GetMapping("/api/me/profile")
	public ProfileResponse myProfile(@AuthenticationPrincipal AuthUser principal) {
		return this.profiles.mine(principal);
	}

}
