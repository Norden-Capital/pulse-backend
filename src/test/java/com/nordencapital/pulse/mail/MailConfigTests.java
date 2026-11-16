package com.nordencapital.pulse.mail;

import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.nordencapital.pulse.config.WebConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which mail sender and executor the app starts with.
 */
class MailConfigTests {

	private static final String[] BASE = { "app.frontend-url=http://localhost:3000",
			"app.mail.from=Pulse <no-reply@example.com>", "app.auth.verify-ttl=24h", "app.auth.reset-ttl=1h",
			"app.auth.login-max-failures=5", "app.auth.login-lock-time=15m",
			"app.auth.forgot-password-max-requests=3", "app.auth.forgot-password-window=15m",
			"app.auth.register-max-per-ip=10", "app.auth.register-window=1h", "app.auth.verify-mail-max-requests=3",
			"app.auth.verify-mail-window=15m", "app.auth.mail-per-email-max=5", "app.auth.mail-per-email-window=1h" };

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(WebConfig.class, MailConfig.class)
		.withPropertyValues(BASE);

	@Test
	void noKeyLocallyPrintsEmailsToTheConsole() {
		this.runner.withPropertyValues("app.mail.resend-api-key=", "app.mail.require-resend=false")
			.run((context) -> assertThat(context).getBean(MailSender.class).isInstanceOf(LoggingMailSender.class));
	}

	@Test
	void keySetUsesResend() {
		this.runner.withPropertyValues("app.mail.resend-api-key=re_test", "app.mail.require-resend=true")
			.run((context) -> assertThat(context).getBean(MailSender.class).isInstanceOf(ResendMailSender.class));
	}

	@Test
	void requiredKeyMissingStopsStartup() {
		this.runner.withPropertyValues("app.mail.resend-api-key=", "app.mail.require-resend=true")
			.run((context) -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.hasMessageContaining("RESEND_API_KEY"));
	}

	@Test
	void asyncUsesABackgroundThreadAndTestsCanTurnItOff() {
		this.runner.withPropertyValues("app.mail.async=true")
			.run((context) -> assertThat(context.getBean(MailConfig.EXECUTOR, Executor.class))
				.isInstanceOf(ThreadPoolTaskExecutor.class));
		this.runner.withPropertyValues("app.mail.async=false")
			.run((context) -> assertThat(context.getBean(MailConfig.EXECUTOR, Executor.class))
				.isInstanceOf(SyncTaskExecutor.class));
	}

}
