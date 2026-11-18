package com.nordencapital.pulse.profile;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The manual override: macros must add up to the calories within ±5 % (hub decision Q4).
 */
class ManualTargetsTests {

	@Test
	void macrosWithinFivePercentAreAccepted() {
		// 4 × 156 + 4 × 403 + 9 × 83 = 2983 for 2980.
		assertThat(new ManualTargets(2980, 156, 403, 83).isMacrosMatchCalories()).isTrue();
		// Exactly 5 % over and under 2000 (2100 and 1900).
		assertThat(new ManualTargets(2000, 100, 300, 0).isMacrosMatchCalories()).isFalse(); // 1600
		assertThat(new ManualTargets(2000, 100, 375, 0).isMacrosMatchCalories()).isTrue(); // 1900
		assertThat(new ManualTargets(2000, 100, 425, 0).isMacrosMatchCalories()).isTrue(); // 2100
	}

	@Test
	void macrosOffByMoreThanFivePercentAreRefused() {
		assertThat(new ManualTargets(2000, 100, 374, 0).isMacrosMatchCalories()).isFalse(); // 1896
		assertThat(new ManualTargets(2000, 100, 426, 0).isMacrosMatchCalories()).isFalse(); // 2104
		assertThat(new ManualTargets(2800, 156, 403, 83).isMacrosMatchCalories()).isFalse(); // 2983, +6.5 %
	}

	@Test
	void missingFieldsAreLeftToNotNull() {
		assertThat(new ManualTargets(null, 1, 1, 1).isMacrosMatchCalories()).isTrue();
	}

}
