package com.nordencapital.pulse.profile;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Formula pulse-energy-v1 (docs/api/onboarding-profile.md §4). The golden rows are the §4
 * table; the first one is the mockup ("Your plan" / "How we calculated this").
 */
class EnergyFormulaTests {

	static ProfileAnswers answers(Sex sex, int age, String heightCm, String weightKg, ActivityLevel level,
			WorkoutsPerWeek workouts, Goal goal) {
		return new ProfileAnswers(goal, sex, age, new BigDecimal(heightCm), new BigDecimal(weightKg), null, level,
				workouts, List.of(), Units.METRIC);
	}

	@Test
	void mockupExample() {
		Plan plan = EnergyFormula.calculate(answers(Sex.MALE, 30, "180", "78", ActivityLevel.SEDENTARY,
				WorkoutsPerWeek.W3_4, Goal.BUILD));

		assertThat(plan.formula()).isEqualTo("pulse-energy-v1");
		assertThat(plan.source()).isEqualTo(Provenance.CALCULATED);
		assertThat(plan.steps().bmr().value()).isEqualTo(1760);
		assertThat(plan.steps().bmr().sexConstant()).isEqualTo(5);
		assertThat(plan.steps().activity().factor()).isEqualByComparingTo("1.55");
		assertThat(plan.steps().activity().value()).isEqualTo(2728);
		assertThat(plan.steps().goal().adjustment()).isEqualTo(250);
		assertThat(plan.steps().goal().beforeRounding()).isEqualTo(2978);
		assertThat(plan.steps().goal().floorApplied()).isFalse();
		assertThat(plan.calories()).isEqualTo(2980);
		assertThat(plan.proteinG()).isEqualTo(156);
		assertThat(plan.carbsG()).isEqualTo(403);
		assertThat(plan.fatG()).isEqualTo(83);
		assertThat(plan.percent()).isEqualTo(new Plan.Percent(21, 54, 25));
		assertThat(plan.steps().macros().proteinCapped()).isFalse();
		assertThat(plan.marginPercent()).isEqualTo(10);
	}

	@ParameterizedTest(name = "{0}, {1} y, {2} cm, {3} kg, {4}, {5}, {6}")
	@CsvSource({
			// sex,      age, cm,  kg,  level,     workouts, goal,  bmr,  factor, tdee, kcal, floor, P,   C,   F,   %P, %C, capped
			"MALE,        30, 180, 78,  SEDENTARY, W3_4,     BUILD, 1760, 1.55,   2728, 2980, false, 156, 403, 83,  21, 54, false",
			"FEMALE,      30, 180, 78,  SEDENTARY, W3_4,     BUILD, 1594, 1.55,   2471, 2720, false, 156, 354, 76,  23, 52, false",
			"UNSPECIFIED, 30, 180, 78,  SEDENTARY, W3_4,     BUILD, 1677, 1.55,   2599, 2850, false, 156, 378, 79,  22, 53, false",
			"FEMALE,      40, 160, 60,  SEDENTARY, W0,       LOSE,  1239, 1.2,    1487, 1200, true,  108, 117, 33,  36, 39, false",
			"MALE,        30, 190, 200, SEDENTARY, W0,       BUILD, 3043, 1.2,    3652, 3900, false, 390, 341, 108, 40, 35, true" })
	void goldenTable(Sex sex, int age, String cm, String kg, ActivityLevel level, WorkoutsPerWeek workouts, Goal goal,
			int bmr, String factor, int tdee, int kcal, boolean floorApplied, int protein, int carbs, int fat,
			int proteinPercent, int carbsPercent, boolean proteinCapped) {
		Plan plan = EnergyFormula.calculate(answers(sex, age, cm, kg, level, workouts, goal));
		assertThat(plan.steps().bmr().value()).isEqualTo(bmr);
		assertThat(plan.steps().activity().factor()).isEqualByComparingTo(factor);
		assertThat(plan.steps().activity().value()).isEqualTo(tdee);
		assertThat(plan.calories()).isEqualTo(kcal);
		assertThat(plan.steps().goal().floorApplied()).isEqualTo(floorApplied);
		assertThat(plan.proteinG()).isEqualTo(protein);
		assertThat(plan.carbsG()).isEqualTo(carbs);
		assertThat(plan.fatG()).isEqualTo(fat);
		assertThat(plan.percent()).isEqualTo(new Plan.Percent(proteinPercent, carbsPercent, 25));
		assertThat(plan.steps().macros().proteinCapped()).isEqualTo(proteinCapped);
	}

	@Test
	void floorIsReportedWithItsValue() {
		Plan plan = EnergyFormula.calculate(answers(Sex.FEMALE, 40, "160", "60", ActivityLevel.SEDENTARY,
				WorkoutsPerWeek.W0, Goal.LOSE));
		assertThat(plan.steps().goal().beforeRounding()).isEqualTo(987);
		assertThat(plan.steps().goal().floor()).isEqualTo(1200);
		assertThat(plan.steps().goal().value()).isEqualTo(1200);
	}

	@ParameterizedTest(name = "{0} + {1} = {2}")
	@CsvSource({ "SEDENTARY, W0, 1.2", "SEDENTARY, W1_2, 1.375", "SEDENTARY, W3_4, 1.55", "SEDENTARY, W5_PLUS, 1.725",
			"LIGHT, W0, 1.375", "LIGHT, W1_2, 1.55", "LIGHT, W3_4, 1.725", "LIGHT, W5_PLUS, 1.9",
			"ACTIVE, W0, 1.55", "ACTIVE, W1_2, 1.725", "ACTIVE, W3_4, 1.9", "ACTIVE, W5_PLUS, 1.9",
			"VERY_ACTIVE, W0, 1.725", "VERY_ACTIVE, W1_2, 1.9", "VERY_ACTIVE, W3_4, 1.9", "VERY_ACTIVE, W5_PLUS, 1.9" })
	void activityFactorMatrix(ActivityLevel level, WorkoutsPerWeek workouts, String factor) {
		assertThat(EnergyFormula.factor(level, workouts)).isEqualByComparingTo(factor);
	}

	@Test
	void goalAdjustmentsFloorsAndProtein() {
		assertThat(EnergyFormula.adjustment(Goal.LOSE)).isEqualTo(-500);
		assertThat(EnergyFormula.adjustment(Goal.MAINTAIN)).isZero();
		assertThat(EnergyFormula.adjustment(Goal.BUILD)).isEqualTo(250);
		assertThat(EnergyFormula.proteinPerKg(Goal.LOSE)).isEqualByComparingTo("1.8");
		assertThat(EnergyFormula.proteinPerKg(Goal.MAINTAIN)).isEqualByComparingTo("1.6");
		assertThat(EnergyFormula.proteinPerKg(Goal.BUILD)).isEqualByComparingTo("2.0");
		assertThat(EnergyFormula.floor(Sex.MALE)).isEqualTo(1500);
		assertThat(EnergyFormula.floor(Sex.FEMALE)).isEqualTo(1200);
		assertThat(EnergyFormula.floor(Sex.UNSPECIFIED)).isEqualTo(1350);
		assertThat(EnergyFormula.sexConstant(Sex.UNSPECIFIED)).isEqualTo(-78);
	}

	@Test
	void roundingIsHalfUp() {
		// BMR 10 × 200 + 6.25 × 190 − 5 × 30 + 5 = 3042.5 → 3043.
		assertThat(EnergyFormula.calculate(answers(Sex.MALE, 30, "190", "200", ActivityLevel.SEDENTARY,
				WorkoutsPerWeek.W0, Goal.BUILD))
			.steps()
			.bmr()
			.value()).isEqualTo(3043);
		// TDEE + adjustment ending in 5 rounds up to the next 10 (e.g. 2975 → 2980).
		Plan endsIn5 = findPlanWhereGoalRoundingEndsIn5();
		assertThat(endsIn5.steps().goal().beforeRounding() % 10).isEqualTo(5);
		assertThat(endsIn5.calories()).isEqualTo(endsIn5.steps().goal().beforeRounding() + 5);
	}

	@Test
	void percentagesAlwaysAddUpTo100AndMacrosMatchCalories() {
		for (Sex sex : Sex.values()) {
			for (Goal goal : Goal.values()) {
				for (ActivityLevel level : ActivityLevel.values()) {
					for (WorkoutsPerWeek workouts : WorkoutsPerWeek.values()) {
						for (String kg : new String[] { "30", "55.5", "78", "120", "300" }) {
							Plan plan = EnergyFormula.calculate(answers(sex, 45, "170", kg, level, workouts, goal));
							Plan.Percent p = plan.percent();
							assertThat(p.protein() + p.carbs() + p.fat()).isEqualTo(100);
							assertThat(plan.calories() % 10).isZero();
							assertThat(plan.calories()).isGreaterThanOrEqualTo(EnergyFormula.floor(sex));
							assertThat(plan.carbsG()).isGreaterThanOrEqualTo(0);
							int macroKcal = 4 * plan.proteinG() + 4 * plan.carbsG() + 9 * plan.fatG();
							assertThat(Math.abs(macroKcal - plan.calories())).isLessThanOrEqualTo(8);
						}
					}
				}
			}
		}
	}

	/** Searches a few ages for a case where TDEE + adjustment ends in 5 (tests half-up to 10). */
	private static Plan findPlanWhereGoalRoundingEndsIn5() {
		for (int age = 18; age <= 100; age++) {
			for (int tenths = 600; tenths <= 900; tenths++) {
				String kg = BigDecimal.valueOf(tenths, 1).toPlainString();
				Plan plan = EnergyFormula.calculate(answers(Sex.MALE, age, "170", kg, ActivityLevel.SEDENTARY,
						WorkoutsPerWeek.W0, Goal.MAINTAIN));
				if (plan.steps().goal().beforeRounding() % 10 == 5) {
					return plan;
				}
			}
		}
		throw new AssertionError("no case ends in 5");
	}

}
