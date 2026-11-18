package com.nordencapital.pulse.profile;

import java.math.BigDecimal;

/**
 * Daily targets calculated by {@link EnergyFormula}, with every number of the four steps,
 * so the "How we calculated this" sheet is rendered from data.
 *
 * @param formula formula id and version, e.g. {@code pulse-energy-v1}
 * @param source always {@code calculated}
 * @param marginPercent how far real needs can differ (shown as a note)
 */
public record Plan(String formula, Provenance source, int calories, int proteinG, int carbsG, int fatG,
		Percent percent, int marginPercent, Steps steps) {

	/** Share of calories per macro; the three add up to 100. */
	public record Percent(int protein, int carbs, int fat) {
	}

	public record Steps(Bmr bmr, Activity activity, GoalStep goal, Macros macros) {
	}

	/** Step 1: energy at rest (Mifflin–St Jeor). */
	public record Bmr(Sex sex, BigDecimal weightKg, BigDecimal heightCm, int ageYears, int sexConstant, int value) {
	}

	/** Step 2: BMR × activity factor. */
	public record Activity(ActivityLevel activityLevel, WorkoutsPerWeek workoutsPerWeek, BigDecimal factor,
			int value) {
	}

	/**
	 * Step 3: goal adjustment, rounded to 10, then the safety floor.
	 * @param beforeRounding TDEE + adjustment
	 */
	public record GoalStep(Goal goal, int adjustment, int beforeRounding, int floor, boolean floorApplied,
			int value) {
	}

	/**
	 * Step 4: protein per kg (capped at 40 % of calories), fat 25 %, carbs the rest.
	 * @param carbs always {@code "rest"}
	 */
	public record Macros(BigDecimal proteinGPerKg, boolean proteinCapped, int fatPercent, String carbs) {
	}

}
