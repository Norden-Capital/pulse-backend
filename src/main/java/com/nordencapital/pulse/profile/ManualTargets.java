package com.nordencapital.pulse.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Targets the user set by hand ("Adjust targets"). Stored with source {@code manual}.
 * The macros must add up to the calories within ±{@value #TOLERANCE_PERCENT} %
 * (4 kcal per g of protein and carbs, 9 per g of fat).
 */
public record ManualTargets(@NotNull @Min(800) @Max(6000) Integer calories, @NotNull @Min(0) @Max(500) Integer proteinG,
		@NotNull @Min(0) @Max(1000) Integer carbsG, @NotNull @Min(0) @Max(400) Integer fatG) {

	static final int TOLERANCE_PERCENT = 5;

	@JsonIgnore
	@AssertTrue(message = "protein, carbs and fat must add up to the calories (within 5%)")
	public boolean isMacrosMatchCalories() {
		if (this.calories == null || this.proteinG == null || this.carbsG == null || this.fatG == null) {
			return true; // the @NotNull rules report it
		}
		int macroKcal = 4 * this.proteinG + 4 * this.carbsG + 9 * this.fatG;
		return Math.abs(macroKcal - this.calories) * 100L <= (long) TOLERANCE_PERCENT * this.calories;
	}

}
