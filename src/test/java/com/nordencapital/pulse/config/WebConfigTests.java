package com.nordencapital.pulse.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Startup behaviour of the proxy secret settings through the real Spring binding.
 */
@ExtendWith(OutputCaptureExtension.class)
class WebConfigTests {

	/** The other required app.* settings, so only the proxy secret varies. */
	static final String[] BASE = { "app.frontend-url=http://localhost:3000", "app.mail.from=Pulse <a@b.c>",
			"app.auth.verify-ttl=24h", "app.auth.reset-ttl=1h", "app.auth.login-max-failures=5",
			"app.auth.login-lock-time=15m", "app.auth.forgot-password-max-requests=3",
			"app.auth.forgot-password-window=15m", "app.auth.register-max-per-ip=10", "app.auth.register-window=1h",
			"app.auth.verify-mail-max-requests=3", "app.auth.verify-mail-window=15m", "app.auth.mail-per-email-max=5",
			"app.auth.mail-per-email-window=1h" };

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(WebConfig.class)
		.withPropertyValues(BASE);

	@Test
	void missingFrontendUrlStopsStartup() {
		this.runner.withPropertyValues("app.proxy-secret=s3cret", "app.frontend-url=${APP_FRONTEND_URL}")
			.run((context) -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.hasMessageContaining("app.frontend-url"));
		this.runner.withPropertyValues("app.proxy-secret=s3cret", "app.frontend-url=")
			.run((context) -> assertThat(context).hasFailed());
	}

	@Test
	void requiredButEmptySecretStopsStartup() {
		this.runner.withPropertyValues("app.require-proxy-secret=true", "app.proxy-secret=")
			.run((context) -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.hasMessageContaining("APP_PROXY_SECRET"));
	}

	@Test
	void unresolvedEnvVarStopsStartup() {
		this.runner.withPropertyValues("app.require-proxy-secret=true", "app.proxy-secret=${APP_PROXY_SECRET_MISSING}")
			.run((context) -> assertThat(context).hasFailed());
	}

	@Test
	void emptySecretLocallyStartsAndLogsThatTheCheckIsOff(CapturedOutput output) {
		this.runner.withPropertyValues("app.require-proxy-secret=false", "app.proxy-secret=")
			.run((context) -> assertThat(context).hasNotFailed());
		assertThat(output).contains("proxy secret check on /api/** is OFF");
	}

	@Test
	void configuredSecretStartsWithoutTheWarning(CapturedOutput output) {
		this.runner.withPropertyValues("app.require-proxy-secret=true", "app.proxy-secret=s3cret")
			.run((context) -> assertThat(context).hasNotFailed());
		assertThat(output).doesNotContain("is OFF");
	}

}
