package com.nordencapital.pulse;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nordencapital.pulse.user.User;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Module 2: POST /api/onboarding/plan, register with a profile, GET /api/me/profile
 * (docs/api/onboarding-profile.md).
 */
class OnboardingProfileIntegrationTests extends IntegrationTestBase {

	/** The mockup answers: male, 30 y, 180 cm, 78 kg, mostly sitting, 3–4 workouts, build. */
	static final String MOCKUP = """
			{"goal":"BUILD","sex":"MALE","ageYears":30,"heightCm":180,"weightKg":78.0,"goalWeightKg":82.0,
			"activityLevel":"SEDENTARY","workoutsPerWeek":"W3_4","trainingTypes":["GYM","CARDIO"],"units":"METRIC"}""";

	/** The plan for {@link #MOCKUP}, exactly as the contract shows it. */
	static final String MOCKUP_PLAN = """
			{"formula":"pulse-energy-v1","source":"calculated","calories":2980,"proteinG":156,"carbsG":403,"fatG":83,
			"percent":{"protein":21,"carbs":54,"fat":25},"marginPercent":10,
			"steps":{
			 "bmr":{"sex":"MALE","weightKg":78.0,"heightCm":180.0,"ageYears":30,"sexConstant":5,"value":1760},
			 "activity":{"activityLevel":"SEDENTARY","workoutsPerWeek":"W3_4","factor":1.55,"value":2728},
			 "goal":{"goal":"BUILD","adjustment":250,"beforeRounding":2978,"floor":1500,"floorApplied":false,"value":2980},
			 "macros":{"proteinGPerKg":2.0,"proteinCapped":false,"fatPercent":25,"carbs":"rest"}}}""";

	private static final String OVERRIDE = "{\"calories\":2800,\"proteinG\":160,\"carbsG\":360,\"fatG\":85}";

	private static String profile(String answers) {
		return "{\"answers\":" + answers + "}";
	}

	private static String profile(String answers, String targets) {
		return "{\"answers\":" + answers + ",\"targets\":" + targets + "}";
	}

	private static String mockupWith(String field, String json) {
		// The value is an array ([...]) or anything up to the next comma or brace.
		String replaced = MOCKUP.replaceFirst("\"" + field + "\":(\\[[^]]*]|[^,}]*)",
				java.util.regex.Matcher.quoteReplacement("\"" + field + "\":" + json));
		if (!replaced.contains("\"" + field + "\":" + json)) {
			throw new IllegalArgumentException("field not found: " + field);
		}
		return replaced;
	}

	private int profileCount() {
		return this.jdbc.queryForObject("SELECT count(*) FROM user_profiles", Integer.class);
	}

	// ----- POST /api/onboarding/plan -----

	@Test
	void planForTheMockupAnswers() {
		Client client = client();
		MvcTestResult result = client.post("/api/onboarding/plan", MOCKUP);
		assertThat(result).hasStatus(HttpStatus.OK).bodyJson().isStrictlyEqualTo(MOCKUP_PLAN);
		// Public: no account, no session, nothing stored.
		assertThat(client.cookie("SESSION")).isNull();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class)).isZero();
		assertThat(profileCount()).isZero();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
	}

	@Test
	void planNeedsTheCsrfToken() {
		assertThat(client().postWithoutCsrf("/api/onboarding/plan", MOCKUP)).hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson()
			.extractingPath("$.error")
			.isEqualTo("csrf_invalid");
	}

	@Test
	void planChecksEveryAnswer() {
		List<String[]> cases = List.of(new String[] { "ageYears", "17" }, new String[] { "ageYears", "101" },
				new String[] { "heightCm", "119.9" }, new String[] { "heightCm", "230.1" },
				new String[] { "heightCm", "180.25" }, new String[] { "weightKg", "29.9" },
				new String[] { "weightKg", "300.1" }, new String[] { "weightKg", "78.05" },
				new String[] { "goalWeightKg", "301" }, new String[] { "trainingTypes", "[\"GYM\",\"GYM\"]" },
				new String[] { "goal", "null" }, new String[] { "sex", "null" }, new String[] { "units", "null" },
				new String[] { "activityLevel", "null" }, new String[] { "workoutsPerWeek", "null" },
				new String[] { "trainingTypes", "null" });
		for (String[] c : cases) {
			MvcTestResult result = client().post("/api/onboarding/plan", mockupWith(c[0], c[1]));
			assertThat(result).as(c[0] + "=" + c[1])
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.error")
				.isEqualTo("validation_error");
			assertThat(json(result)).as(c[0] + "=" + c[1]).contains(c[0]);
		}
		// Unknown enum value: not valid JSON for the type.
		assertThat(client().post("/api/onboarding/plan", mockupWith("goal", "\"BULK\""))).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.error")
			.isEqualTo("bad_request");
		// The edges are fine.
		for (String[] ok : List.of(new String[] { "ageYears", "18" }, new String[] { "ageYears", "100" },
				new String[] { "heightCm", "120" }, new String[] { "heightCm", "230.0" },
				new String[] { "weightKg", "30" }, new String[] { "weightKg", "300.0" },
				new String[] { "goalWeightKg", "null" }, new String[] { "trainingTypes", "[]" })) {
			assertThat(client().post("/api/onboarding/plan", mockupWith(ok[0], ok[1]))).as(ok[0] + "=" + ok[1])
				.hasStatus(HttpStatus.OK);
		}
	}

	@Test
	void planIsLimitedPerIp() {
		Client client = client("198.51.100.20");
		for (int i = 0; i < 60; i++) {
			assertThat(client.post("/api/onboarding/plan", MOCKUP)).hasStatus(HttpStatus.OK);
		}
		assertThat(client.post("/api/onboarding/plan", MOCKUP)).hasStatus(HttpStatus.TOO_MANY_REQUESTS)
			.bodyJson()
			.extractingPath("$.error")
			.isEqualTo("too_many_attempts");
		assertThat(client("198.51.100.21").post("/api/onboarding/plan", MOCKUP)).hasStatus(HttpStatus.OK);
	}

	// ----- register with a profile -----

	@Test
	void newAccountStoresTheServerCalculatedProfile() {
		assertThat(client().register("new@example.com", PASSWORD, profile(MOCKUP))).hasStatus(HttpStatus.ACCEPTED)
			.bodyText()
			.isEmpty();
		Map<String, Object> row = this.jdbc.queryForMap(
				"SELECT p.* FROM user_profiles p JOIN users u ON u.id = p.user_id WHERE u.email = 'new@example.com'");
		assertThat(row).containsEntry("targets_source", "calculated")
			.containsEntry("measurements_source", "manual")
			.containsEntry("formula_version", "pulse-energy-v1")
			.containsEntry("target_kcal", 2980)
			.containsEntry("target_protein_g", 156)
			.containsEntry("target_carbs_g", 403)
			.containsEntry("target_fat_g", 83)
			.containsEntry("calculated_kcal", 2980)
			.containsEntry("goal", "BUILD")
			.containsEntry("age_years", 30)
			.containsEntry("units", "METRIC");
		assertThat(row.get("height_cm").toString()).isEqualTo("180.0");
		assertThat(row.get("weight_kg").toString()).isEqualTo("78.0");
		assertThat(row.get("goal_weight_kg").toString()).isEqualTo("82.0");
		assertThat(this.mails.to("new@example.com")).hasSize(1);
	}

	@Test
	void manualOverrideIsStoredAsManualNextToTheCalculatedPlan() {
		client().register("new@example.com", PASSWORD, profile(MOCKUP, OVERRIDE));
		Map<String, Object> row = this.jdbc.queryForMap("SELECT * FROM user_profiles");
		assertThat(row).containsEntry("targets_source", "manual")
			.containsEntry("target_kcal", 2800)
			.containsEntry("target_protein_g", 160)
			.containsEntry("target_carbs_g", 360)
			.containsEntry("target_fat_g", 85)
			.containsEntry("calculated_kcal", 2980)
			.containsEntry("calculated_protein_g", 156);
	}

	@Test
	void clientMadeCalculatedNumbersAreIgnored() {
		String sneaky = "{\"answers\":" + MOCKUP + ",\"plan\":{\"calories\":9999,\"proteinG\":1}}";
		String answersWithCalories = MOCKUP.replace("{\"goal\"", "{\"calories\":9999,\"goal\"");
		client().register("one@example.com", PASSWORD, sneaky);
		client().register("two@example.com", PASSWORD, profile(answersWithCalories));
		assertThat(this.jdbc.queryForList("SELECT target_kcal FROM user_profiles", Integer.class)).containsOnly(2980);
		assertThat(this.jdbc.queryForList("SELECT targets_source FROM user_profiles", String.class))
			.containsOnly("calculated");
	}

	@Test
	void pendingAccountTakesTheLatestProfile() {
		client().register("new@example.com", PASSWORD, profile(MOCKUP));
		String lose = mockupWith("goal", "\"LOSE\"");
		assertThat(client().register("new@example.com", PASSWORD, profile(lose))).hasStatus(HttpStatus.ACCEPTED);
		assertThat(this.jdbc.queryForObject("SELECT goal FROM user_profiles", String.class)).isEqualTo("LOSE");
		assertThat(profileCount()).isEqualTo(1);
	}

	@Test
	void pendingAccountKeepsItsProfileWhenTheNewRegisterHasNone() {
		client().register("new@example.com", PASSWORD, profile(MOCKUP));
		assertThat(client().register("new@example.com", "another-strong-password")).hasStatus(HttpStatus.ACCEPTED);
		assertThat(this.jdbc.queryForObject("SELECT target_kcal FROM user_profiles", Integer.class)).isEqualTo(2980);
	}

	@Test
	void activeAccountProfileIsNeverTouched() {
		activeUser("ana@example.com");
		assertThat(client().register("ana@example.com", PASSWORD, profile(MOCKUP))).hasStatus(HttpStatus.ACCEPTED)
			.bodyText()
			.isEmpty();
		assertThat(profileCount()).isZero();
		assertThat(this.mails.to("ana@example.com").get(0).subject()).isEqualTo("You already have a Pulse account");

		// With an existing profile: still unchanged.
		pendingProfileFor("bob@example.com");
		this.jdbc.update("UPDATE users SET status = 'ACTIVE' WHERE email = 'bob@example.com'");
		client().register("bob@example.com", PASSWORD, profile(mockupWith("goal", "\"LOSE\"")));
		assertThat(this.jdbc.queryForObject("SELECT goal FROM user_profiles", String.class)).isEqualTo("BUILD");
	}

	@Test
	void disabledAccountGetsNoProfile() {
		disabledUser("blocked@example.com");
		assertThat(client().register("blocked@example.com", PASSWORD, profile(MOCKUP)))
			.hasStatus(HttpStatus.ACCEPTED);
		assertThat(profileCount()).isZero();
	}

	@Test
	void theAnswerIsTheSameInEveryCase() {
		activeUser("active@example.com");
		pendingUser("pending@example.com");
		disabledUser("disabled@example.com");
		for (String email : List.of("nobody@example.com", "pending@example.com", "active@example.com",
				"disabled@example.com")) {
			MvcTestResult result = client().register(email, PASSWORD, profile(MOCKUP, OVERRIDE));
			assertThat(result).as(email).hasStatus(HttpStatus.ACCEPTED);
			assertThat(json(result)).isEmpty();
		}
	}

	@Test
	void badProfileStopsTheRegister() {
		for (String bad : List.of(profile(mockupWith("ageYears", "17")), "{\"targets\":" + OVERRIDE + "}",
				profile(MOCKUP, "{\"calories\":2800,\"proteinG\":160,\"carbsG\":360}"),
				profile(MOCKUP, "{\"calories\":700,\"proteinG\":40,\"carbsG\":100,\"fatG\":20}"),
				// 4 × 156 + 4 × 403 + 9 × 83 = 2983, more than 5 % over 2800.
				profile(MOCKUP, "{\"calories\":2800,\"proteinG\":156,\"carbsG\":403,\"fatG\":83}"))) {
			MvcTestResult result = client().register("new@example.com", PASSWORD, bad);
			assertThat(result).as(bad)
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.error")
				.isEqualTo("validation_error");
		}
		assertThat(this.users.findByEmail("new@example.com")).isEmpty();
		assertThat(this.mails.all()).isEmpty();
		assertThat(client().register("new@example.com", PASSWORD,
				profile(MOCKUP, "{\"calories\":2800,\"proteinG\":156,\"carbsG\":358,\"fatG\":78}")))
			.hasStatus(HttpStatus.ACCEPTED);
	}

	@Test
	void overTheMailLimitNothingIsStored() {
		Client client = client("198.51.100.30");
		// Uses up the 3 verification mails for this email + IP...
		for (int i = 0; i < 3; i++) {
			client.register("new@example.com", PASSWORD, profile(MOCKUP));
		}
		// ...then make the email unknown again: the next register is over the limit.
		this.jdbc.update("DELETE FROM users WHERE email = 'new@example.com'");
		assertThat(client.register("new@example.com", PASSWORD, profile(MOCKUP))).hasStatus(HttpStatus.ACCEPTED);
		assertThat(this.users.findByEmail("new@example.com")).isEmpty();
		assertThat(profileCount()).isZero();
	}

	@Test
	void registerWithoutAProfileStillWorks() {
		assertThat(client().register("new@example.com", PASSWORD)).hasStatus(HttpStatus.ACCEPTED);
		assertThat(this.users.findByEmail("new@example.com")).isPresent();
		assertThat(profileCount()).isZero();
	}

	// ----- GET /api/me/profile -----

	@Test
	void profileIsThereAfterVerifyAndSignIn() throws Exception {
		client().register("new@example.com", PASSWORD, profile(MOCKUP));
		String token = tokenFromLastMailTo("new@example.com");
		client().post("/api/auth/verify-email", "{\"token\":\"" + token + "\"}");
		Client client = loggedIn("new@example.com");

		MvcTestResult result = client.get("/api/me/profile");
		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$.measurementsSource").isEqualTo("manual");
		assertThat(result).bodyJson()
			.extractingPath("$.targets")
			.isEqualTo(Map.of("calories", 2980, "proteinG", 156, "carbsG", 403, "fatG", 83, "source", "calculated"));
		assertThat(result).bodyJson().extractingPath("$.answers.goal").isEqualTo("BUILD");
		assertThat(result).bodyJson().extractingPath("$.answers.heightCm").isEqualTo(180.0);
		assertThat(result).bodyJson().extractingPath("$.answers.goalWeightKg").isEqualTo(82.0);
		assertThat(result).bodyJson()
			.extractingPath("$.answers.trainingTypes")
			.asArray()
			.containsExactly("GYM", "CARDIO");
		assertThat(result).bodyJson().extractingPath("$.answeredAt").isNotNull();
		String calculated = new tools.jackson.databind.json.JsonMapper().readTree(json(result))
			.get("calculated")
			.toString();
		org.skyscreamer.jsonassert.JSONAssert.assertEquals(MOCKUP_PLAN, calculated, true);
	}

	@Test
	void profileShowsTheManualOverrideAndStillTheCalculatedPlan() {
		client().register("new@example.com", PASSWORD, profile(MOCKUP, OVERRIDE));
		this.jdbc.update("UPDATE users SET status = 'ACTIVE'");
		MvcTestResult result = loggedIn("new@example.com").get("/api/me/profile");
		assertThat(result).bodyJson().extractingPath("$.targets.source").isEqualTo("manual");
		assertThat(result).bodyJson().extractingPath("$.targets.calories").isEqualTo(2800);
		assertThat(result).bodyJson().extractingPath("$.calculated.calories").isEqualTo(2980);
	}

	@Test
	void noProfileGives404AndNoSessionGives401() {
		activeUser("ana@example.com");
		assertThat(loggedIn("ana@example.com").get("/api/me/profile")).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.isStrictlyEqualTo("{\"error\":\"profile_not_found\",\"message\":\"No profile yet\"}");
		assertThat(client().get("/api/me/profile")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void everyoneSeesOnlyTheirOwnProfile() {
		pendingProfileFor("bob@example.com");
		activeUser("ana@example.com");
		assertThat(loggedIn("ana@example.com").get("/api/me/profile")).hasStatus(HttpStatus.NOT_FOUND);
	}

	/** A pending account with the mockup profile, made through register. */
	private User pendingProfileFor(String email) {
		client().register(email, PASSWORD, profile(MOCKUP));
		return this.users.findByEmail(email).orElseThrow();
	}

}
