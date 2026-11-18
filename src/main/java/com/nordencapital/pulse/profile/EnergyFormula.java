package com.nordencapital.pulse.profile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Formula {@value #ID}: daily calories and macros from the onboarding answers. The exact
 * rules are in docs/api/onboarding-profile.md §4; change them only with a new formula id.
 * <ol>
 * <li>BMR (Mifflin–St Jeor): 10 × kg + 6.25 × cm − 5 × age + s; s = +5 male, −161 female,
 * −78 prefer not to say (the average);</li>
 * <li>TDEE = BMR × activity factor (activity level × workouts per week);</li>
 * <li>calories = TDEE + goal adjustment, rounded to 10, at least the floor for the sex;</li>
 * <li>protein g/kg of current weight (at most 40 % of calories), fat 25 % of calories,
 * carbs the rest.</li>
 * </ol>
 * Every step uses the previous step's rounded value, so the numbers on screen add up.
 * Rounding is half-up. Pure and stateless.
 */
public final class EnergyFormula {

	public static final String ID = "pulse-energy-v1";

	static final int MARGIN_PERCENT = 10;

	static final int FAT_PERCENT = 25;

	private static final BigDecimal FAT_SHARE = new BigDecimal("0.25");

	private static final BigDecimal PROTEIN_MAX_SHARE = new BigDecimal("0.40");

	/**
	 * The classic ladder. Mostly sitting uses it by workouts per week (0 → 1.2, 1–2 → 1.375,
	 * 3–4 → 1.55, 5+ → 1.725); each higher activity level moves one rung up, capped at 1.9.
	 */
	private static final List<BigDecimal> LADDER = List.of(new BigDecimal("1.2"), new BigDecimal("1.375"),
			new BigDecimal("1.55"), new BigDecimal("1.725"), new BigDecimal("1.9"));

	private EnergyFormula() {
	}

	public static Plan calculate(ProfileAnswers answers) {
		BigDecimal weight = answers.weightKg().setScale(1, RoundingMode.UNNECESSARY);
		BigDecimal height = answers.heightCm().setScale(1, RoundingMode.UNNECESSARY);
		int age = answers.ageYears();

		// Step 1: energy at rest.
		int sexConstant = sexConstant(answers.sex());
		int bmr = round(BigDecimal.TEN.multiply(weight)
			.add(new BigDecimal("6.25").multiply(height))
			.subtract(BigDecimal.valueOf(5L * age))
			.add(BigDecimal.valueOf(sexConstant)));

		// Step 2: daily activity.
		BigDecimal factor = factor(answers.activityLevel(), answers.workoutsPerWeek());
		int tdee = round(BigDecimal.valueOf(bmr).multiply(factor));

		// Step 3: goal, rounded to 10, then the floor.
		int adjustment = adjustment(answers.goal());
		int beforeRounding = tdee + adjustment;
		int rounded = round(BigDecimal.valueOf(beforeRounding).movePointLeft(1)) * 10;
		int floor = floor(answers.sex());
		boolean floorApplied = rounded < floor;
		int calories = Math.max(rounded, floor);

		// Step 4: macros.
		BigDecimal proteinPerKg = proteinPerKg(answers.goal());
		int proteinWanted = round(proteinPerKg.multiply(weight));
		int proteinCap = round(PROTEIN_MAX_SHARE.multiply(BigDecimal.valueOf(calories)).divide(BigDecimal.valueOf(4)));
		boolean proteinCapped = proteinWanted > proteinCap;
		int protein = Math.min(proteinWanted, proteinCap);
		BigDecimal fatKcal = FAT_SHARE.multiply(BigDecimal.valueOf(calories));
		int fat = round(fatKcal.divide(BigDecimal.valueOf(9), 6, RoundingMode.HALF_UP));
		int carbs = round(BigDecimal.valueOf(calories)
			.subtract(BigDecimal.valueOf(4L * protein))
			.subtract(fatKcal)
			.divide(BigDecimal.valueOf(4), 6, RoundingMode.HALF_UP));
		int proteinPercent = round(BigDecimal.valueOf(400L * protein)
			.divide(BigDecimal.valueOf(calories), 6, RoundingMode.HALF_UP));

		return new Plan(ID, Provenance.CALCULATED, calories, protein, carbs, fat,
				new Plan.Percent(proteinPercent, 100 - proteinPercent - FAT_PERCENT, FAT_PERCENT), MARGIN_PERCENT,
				new Plan.Steps(new Plan.Bmr(answers.sex(), weight, height, age, sexConstant, bmr),
						new Plan.Activity(answers.activityLevel(), answers.workoutsPerWeek(), factor, tdee),
						new Plan.GoalStep(answers.goal(), adjustment, beforeRounding, floor, floorApplied, calories),
						new Plan.Macros(proteinPerKg, proteinCapped, FAT_PERCENT, "rest")));
	}

	static BigDecimal factor(ActivityLevel level, WorkoutsPerWeek workouts) {
		return LADDER.get(Math.min(level.ordinal() + workouts.ordinal(), LADDER.size() - 1));
	}

	static int sexConstant(Sex sex) {
		return switch (sex) {
			case MALE -> 5;
			case FEMALE -> -161;
			case UNSPECIFIED -> -78;
		};
	}

	static int floor(Sex sex) {
		return switch (sex) {
			case MALE -> 1500;
			case FEMALE -> 1200;
			case UNSPECIFIED -> 1350;
		};
	}

	static int adjustment(Goal goal) {
		return switch (goal) {
			case LOSE -> -500;
			case MAINTAIN -> 0;
			case BUILD -> 250;
		};
	}

	static BigDecimal proteinPerKg(Goal goal) {
		return switch (goal) {
			case LOSE -> new BigDecimal("1.8");
			case MAINTAIN -> new BigDecimal("1.6");
			case BUILD -> new BigDecimal("2.0");
		};
	}

	/** Half-up to a whole number. */
	private static int round(BigDecimal value) {
		return value.setScale(0, RoundingMode.HALF_UP).intValueExact();
	}

}
