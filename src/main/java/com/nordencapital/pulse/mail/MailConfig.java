package com.nordencapital.pulse.mail;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.nordencapital.pulse.config.AppProperties;

@Configuration(proxyBeanMethods = false)
class MailConfig {

	static final String EXECUTOR = "mailExecutor";

	@Bean
	MailSender mailSender(AppProperties properties) {
		AppProperties.Mail mail = properties.mail();
		if (mail.hasResendApiKey()) {
			return new ResendMailSender(mail.resendApiKey(), mail.from());
		}
		if (mail.requireResend()) {
			throw new IllegalStateException("RESEND_API_KEY must be set (app.mail.require-resend is true)");
		}
		return new LoggingMailSender();
	}

	/**
	 * Background thread for emails (app.mail.async). Small, bounded queue: if it is full
	 * the email is dropped and logged (the user can ask again). Tests turn async off, so
	 * emails are captured before the request returns.
	 */
	@Bean(name = EXECUTOR)
	Executor mailExecutor(AppProperties properties) {
		if (!properties.mail().async()) {
			return new SyncTaskExecutor();
		}
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("mail-");
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(500);
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(15);
		// Spring starts it (afterPropertiesSet) and shuts it down with the app.
		return executor;
	}

}
