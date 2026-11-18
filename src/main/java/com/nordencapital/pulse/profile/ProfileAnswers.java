package com.nordencapital.pulse.profile;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The onboarding answers. Always metric: the frontend converts imperial input before
 * sending (docs/api/onboarding-profile.md §1).
 *
 * @param goalWeightKg optional; not used in the formula
 * @param trainingTypes distinct, may be empty; not used in the formula
 * @param units display preference only
 */
public record ProfileAnswers(@NotNull Goal goal, @NotNull Sex sex,
		@NotNull @Min(value = 18, message = "must be between 18 and 100") @Max(value = 100,
				message = "must be between 18 and 100") Integer ageYears,
		@NotNull @DecimalMin(value = "120.0", message = "must be between 120 and 230") @DecimalMax(value = "230.0",
				message = "must be between 120 and 230") @Digits(integer = 3, fraction = 1,
						message = "must have at most 1 decimal") BigDecimal heightCm,
		@NotNull @DecimalMin(value = "30.0", message = "must be between 30 and 300") @DecimalMax(value = "300.0",
				message = "must be between 30 and 300") @Digits(integer = 3, fraction = 1,
						message = "must have at most 1 decimal") BigDecimal weightKg,
		@DecimalMin(value = "30.0", message = "must be between 30 and 300") @DecimalMax(value = "300.0",
				message = "must be between 30 and 300") @Digits(integer = 3, fraction = 1,
						message = "must have at most 1 decimal") BigDecimal goalWeightKg,
		@NotNull ActivityLevel activityLevel, @NotNull WorkoutsPerWeek workoutsPerWeek,
		@NotNull List<@NotNull TrainingType> trainingTypes, @NotNull Units units) {

	/** Each training type at most once. */
	@JsonIgnore
	@AssertTrue(message = "must not repeat a training type")
	public boolean isTrainingTypesDistinct() {
		return this.trainingTypes == null || new HashSet<>(this.trainingTypes).size() == this.trainingTypes.size();
	}

}
