package com.nordencapital.pulse.profile;

import java.time.Instant;

/**
 * GET /api/me/profile.
 *
 * @param measurementsSource where height and weight come from ({@code manual} here)
 * @param targets the targets the app uses
 * @param calculated the plan from the stored answers and formula version, with its steps
 */
public record ProfileResponse(ProfileAnswers answers, Provenance measurementsSource, Targets targets,
		Plan calculated, Instant answeredAt, Instant updatedAt) {

	/** @param source {@code calculated} or {@code manual} (the user's override) */
	public record Targets(int calories, int proteinG, int carbsG, int fatG, Provenance source) {
	}

	static ProfileResponse from(UserProfile profile, Plan calculated) {
		return new ProfileResponse(profile.answers(), profile.getMeasurementsSource(),
				new Targets(profile.getTargetKcal(), profile.getTargetProteinG(), profile.getTargetCarbsG(),
						profile.getTargetFatG(), profile.getTargetsSource()),
				calculated, profile.getAnsweredAt(), profile.getUpdatedAt());
	}

}
