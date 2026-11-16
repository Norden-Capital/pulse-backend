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

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(WebConfig.class);

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
