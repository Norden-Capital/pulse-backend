package com.nordencapital.pulse.profile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The onboarding answers and daily targets of one user (table user_profiles). Values are
 * metric. The calculated plan is kept next to the targets in use, so a manual override
 * never hides what the formula said.
 */
@Entity
@Table(name = "user_profiles")
public class UserProfile {

	@Id
	@Column(name = "user_id")
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Goal goal;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Sex sex;

	@Column(name = "age_years", nullable = false)
	private short ageYears;

	@Column(name = "height_cm", nullable = false, precision = 4, scale = 1)
	private BigDecimal heightCm;

	@Column(name = "weight_kg", nullable = false, precision = 4, scale = 1)
	private BigDecimal weightKg;

	@Column(name = "goal_weight_kg", precision = 4, scale = 1)
	private BigDecimal goalWeightKg;

	@Enumerated(EnumType.STRING)
	@Column(name = "activity_level", nullable = false)
	private ActivityLevel activityLevel;

	@Enumerated(EnumType.STRING)
	@Column(name = "workouts_per_week", nullable = false)
	private WorkoutsPerWeek workoutsPerWeek;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "training_types", nullable = false)
	private String[] trainingTypes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Units units;

	@Convert(converter = Provenance.DbConverter.class)
	@Column(name = "measurements_source", nullable = false)
	private Provenance measurementsSource;

	@Column(name = "formula_version", nullable = false)
	private String formulaVersion;

	@Column(name = "calculated_kcal", nullable = false)
	private int calculatedKcal;

	@Column(name = "calculated_protein_g", nullable = false)
	private int calculatedProteinG;

	@Column(name = "calculated_carbs_g", nullable = false)
	private int calculatedCarbsG;

	@Column(name = "calculated_fat_g", nullable = false)
	private int calculatedFatG;

	@Column(name = "target_kcal", nullable = false)
	private int targetKcal;

	@Column(name = "target_protein_g", nullable = false)
	private int targetProteinG;

	@Column(name = "target_carbs_g", nullable = false)
	private int targetCarbsG;

	@Column(name = "target_fat_g", nullable = false)
	private int targetFatG;

	@Convert(converter = Provenance.DbConverter.class)
	@Column(name = "targets_source", nullable = false)
	private Provenance targetsSource;

	@Column(name = "answered_at", nullable = false)
	private Instant answeredAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected UserProfile() {
	}

	UserProfile(UUID userId, Instant now) {
		this.userId = userId;
		this.createdAt = now;
	}

	/**
	 * Replaces everything with these answers, their calculated plan and the targets in use.
	 * @param override the manual targets, or null (targets = calculated)
	 */
	void update(ProfileAnswers answers, Plan plan, ManualTargets override, Instant now) {
		this.goal = answers.goal();
		this.sex = answers.sex();
		this.ageYears = answers.ageYears().shortValue();
		this.heightCm = answers.heightCm();
		this.weightKg = answers.weightKg();
		this.goalWeightKg = answers.goalWeightKg();
		this.activityLevel = answers.activityLevel();
		this.workoutsPerWeek = answers.workoutsPerWeek();
		this.trainingTypes = answers.trainingTypes().stream().map(Enum::name).toArray(String[]::new);
		this.units = answers.units();
		this.measurementsSource = Provenance.MANUAL;
		this.formulaVersion = plan.formula();
		this.calculatedKcal = plan.calories();
		this.calculatedProteinG = plan.proteinG();
		this.calculatedCarbsG = plan.carbsG();
		this.calculatedFatG = plan.fatG();
		if (override != null) {
			this.targetKcal = override.calories();
			this.targetProteinG = override.proteinG();
			this.targetCarbsG = override.carbsG();
			this.targetFatG = override.fatG();
			this.targetsSource = Provenance.MANUAL;
		}
		else {
			this.targetKcal = plan.calories();
			this.targetProteinG = plan.proteinG();
			this.targetCarbsG = plan.carbsG();
			this.targetFatG = plan.fatG();
			this.targetsSource = Provenance.CALCULATED;
		}
		this.answeredAt = now;
		this.updatedAt = now;
	}

	public UUID getUserId() {
		return this.userId;
	}

	/** The stored answers, as the API takes them. */
	public ProfileAnswers answers() {
		List<TrainingType> types = Arrays.stream(this.trainingTypes).map(TrainingType::valueOf).toList();
		return new ProfileAnswers(this.goal, this.sex, (int) this.ageYears, this.heightCm, this.weightKg,
				this.goalWeightKg, this.activityLevel, this.workoutsPerWeek, types, this.units);
	}

	public Provenance getMeasurementsSource() {
		return this.measurementsSource;
	}

	public String getFormulaVersion() {
		return this.formulaVersion;
	}

	public int getCalculatedKcal() {
		return this.calculatedKcal;
	}

	public int getTargetKcal() {
		return this.targetKcal;
	}

	public int getTargetProteinG() {
		return this.targetProteinG;
	}

	public int getTargetCarbsG() {
		return this.targetCarbsG;
	}

	public int getTargetFatG() {
		return this.targetFatG;
	}

	public Provenance getTargetsSource() {
		return this.targetsSource;
	}

	public Instant getAnsweredAt() {
		return this.answeredAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

}
