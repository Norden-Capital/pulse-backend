package com.nordencapital.pulse.config;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class AppPropertiesTests {

	private static final AppProperties.Mail MAIL = new AppProperties.Mail("Pulse <a@b.c>", "", false, true);

	private static final AppProperties.Auth AUTH = new AppProperties.Auth(Duration.ofHours(24), Duration.ofHours(1), 5,
			Duration.ofMinutes(15), 3, Duration.ofMinutes(15), 10, Duration.ofHours(1), 3, Duration.ofMinutes(15), 5,
			Duration.ofHours(1));

	private static AppProperties properties(String secret, boolean required) {
		return new AppProperties(secret, required, "http://localhost:3000", MAIL, AUTH);
	}

	@Test
	void requiredSecretMustNotBeEmpty() {
		assertThatIllegalStateException().isThrownBy(() -> properties("", true))
			.withMessageContaining("APP_PROXY_SECRET");
		assertThatIllegalStateException().isThrownBy(() -> properties(null, true));
		assertThatIllegalStateException().isThrownBy(() -> properties("   ", true));
	}

	@Test
	void missingEnvVarFailsEvenWhenNotRequired() {
		assertThatIllegalStateException().isThrownBy(() -> properties("${APP_PROXY_SECRET}", false));
	}

	@Test
	void emptySecretIsAllowedLocally() {
		assertThat(properties("", false).hasProxySecret()).isFalse();
		assertThat(properties("s3cret", true).hasProxySecret()).isTrue();
	}

	@Test
	void missingFrontendUrlOrMailSettingFails() {
		assertThatIllegalStateException()
			.isThrownBy(() -> new AppProperties("", false, "${APP_FRONTEND_URL}", MAIL, AUTH))
			.withMessageContaining("app.frontend-url");
		assertThatIllegalStateException().isThrownBy(() -> new AppProperties.Mail("${APP_MAIL_FROM}", "", true, true))
			.withMessageContaining("app.mail.from");
		assertThatIllegalStateException()
			.isThrownBy(() -> new AppProperties.Mail("Pulse <a@b.c>", "${RESEND_API_KEY}", true, true))
			.withMessageContaining("app.mail.resend-api-key");
	}

	@Test
	void resendKeyIsOptionalLocally() {
		assertThat(MAIL.hasResendApiKey()).isFalse();
		assertThat(new AppProperties.Mail("Pulse <a@b.c>", "re_123", true, true).hasResendApiKey()).isTrue();
	}

}
