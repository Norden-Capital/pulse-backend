-- Onboarding answers and daily targets, one row per user. Values are metric.
-- Provenance (CLAUDE.md): body measurements typed here are 'manual'; targets are 'calculated'
-- (formula_version + the answers make them reproducible) or 'manual' (the user's override).
CREATE TABLE user_profiles (
    user_id              UUID          PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    -- answers (typed by the user)
    goal                 VARCHAR(20)   NOT NULL,
    sex                  VARCHAR(20)   NOT NULL,
    age_years            SMALLINT      NOT NULL,  -- as answered, at answered_at (no birth date)
    height_cm            NUMERIC(4, 1) NOT NULL,
    weight_kg            NUMERIC(4, 1) NOT NULL,
    goal_weight_kg       NUMERIC(4, 1),
    activity_level       VARCHAR(20)   NOT NULL,
    workouts_per_week    VARCHAR(10)   NOT NULL,
    training_types       VARCHAR(20)[] NOT NULL DEFAULT '{}',
    units                VARCHAR(10)   NOT NULL,
    measurements_source  VARCHAR(20)   NOT NULL DEFAULT 'manual',
    -- the plan the formula gave at that time
    formula_version      VARCHAR(40)   NOT NULL,
    calculated_kcal      INTEGER       NOT NULL,
    calculated_protein_g INTEGER       NOT NULL,
    calculated_carbs_g   INTEGER       NOT NULL,
    calculated_fat_g     INTEGER       NOT NULL,
    -- the targets the app uses (= calculated, or the manual override)
    target_kcal          INTEGER       NOT NULL,
    target_protein_g     INTEGER       NOT NULL,
    target_carbs_g       INTEGER       NOT NULL,
    target_fat_g         INTEGER       NOT NULL,
    targets_source       VARCHAR(20)   NOT NULL,
    answered_at          TIMESTAMPTZ   NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    CONSTRAINT user_profiles_goal_ck CHECK (goal IN ('LOSE', 'MAINTAIN', 'BUILD')),
    CONSTRAINT user_profiles_sex_ck CHECK (sex IN ('MALE', 'FEMALE', 'UNSPECIFIED')),
    CONSTRAINT user_profiles_age_ck CHECK (age_years BETWEEN 18 AND 100),
    CONSTRAINT user_profiles_height_ck CHECK (height_cm BETWEEN 120 AND 230),
    CONSTRAINT user_profiles_weight_ck CHECK (weight_kg BETWEEN 30 AND 300),
    CONSTRAINT user_profiles_goal_weight_ck CHECK (goal_weight_kg IS NULL OR goal_weight_kg BETWEEN 30 AND 300),
    CONSTRAINT user_profiles_activity_ck CHECK (activity_level IN ('SEDENTARY', 'LIGHT', 'ACTIVE', 'VERY_ACTIVE')),
    CONSTRAINT user_profiles_workouts_ck CHECK (workouts_per_week IN ('W0', 'W1_2', 'W3_4', 'W5_PLUS')),
    CONSTRAINT user_profiles_training_ck CHECK (training_types <@ ARRAY['GYM', 'HOME', 'CARDIO']::VARCHAR(20)[]),
    CONSTRAINT user_profiles_units_ck CHECK (units IN ('METRIC', 'IMPERIAL')),
    CONSTRAINT user_profiles_measurements_source_ck CHECK (measurements_source IN ('manual', 'calculated', 'device')),
    CONSTRAINT user_profiles_targets_source_ck CHECK (targets_source IN ('calculated', 'manual'))
);
