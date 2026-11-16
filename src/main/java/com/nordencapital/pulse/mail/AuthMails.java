package com.nordencapital.pulse.mail;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.nordencapital.pulse.config.AppProperties;

/**
 * Builds and sends the auth emails: Verify, Reset and Account exists.
 * <p>
 * Emails are sent after the database transaction commits, so a rolled back change never
 * sends an email. They are handed to the mail executor (a background thread in prod), so
 * the response time of register / resend / forgot password does not show whether an
 * email was sent. A failed send is logged and does not fail the request (the user can
 * ask again).
 */
@Component
public class AuthMails {

	private static final Logger logger = LoggerFactory.getLogger(AuthMails.class);

	private final MailSender sender;

	private final Executor executor;

	private final String frontendUrl;

	private final AppProperties.Auth settings;

	public AuthMails(MailSender sender, @Qualifier(MailConfig.EXECUTOR) Executor executor, AppProperties properties) {
		this.sender = sender;
		this.executor = executor;
		this.frontendUrl = stripTrailingSlash(properties.frontendUrl());
		this.settings = properties.auth();
	}

	public void sendVerify(String to, String rawToken) {
		send(to, "Confirm your email for Pulse",
				new EmailLayout.ActionEmail("Confirm your email to start using Pulse.", "Confirm your email",
						"Click the button below to confirm your email and start using Pulse. This link expires in "
								+ noBreak(timeLeft(this.settings.verifyTtl())) + ".",
						"Confirm email", this.frontendUrl + "/verify-email?token=" + rawToken));
	}

	public void sendPasswordReset(String to, String rawToken) {
		send(to, "Reset your Pulse password",
				new EmailLayout.ActionEmail("Use this link to choose a new Pulse password.",
						"Reset your Pulse password",
						"Click the button below to choose a new password. This link expires in "
								+ noBreak(timeLeft(this.settings.resetTtl())) + ".",
						"Reset password", this.frontendUrl + "/set-password?token=" + rawToken));
	}

	/** Answer to a register with an email that already has an ACTIVE account. No token. */
	public void sendAccountExists(String to) {
		send(to, "You already have a Pulse account",
				new EmailLayout.ActionEmail("Someone tried to sign up with this email.",
						"You already have a Pulse account",
						"Someone, maybe you, tried to create a Pulse account with this email. You already have one. "
								+ "Sign in, or reset your password if you forgot it.",
						"Sign in", this.frontendUrl + "/login", "Forgot your password?",
						this.frontendUrl + "/forgot-password"));
	}

	private void send(String to, String subject, EmailLayout.ActionEmail email) {
		sendAfterCommit(new MailMessage(to, subject, EmailLayout.html(email), EmailLayout.text(email)));
	}

	private void sendAfterCommit(MailMessage message) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					hand(message);
				}
			});
		}
		else {
			hand(message);
		}
	}

	private void hand(MailMessage message) {
		try {
			this.executor.execute(() -> sendNow(message));
		}
		catch (RejectedExecutionException ex) {
			logger.error("Mail queue is full, dropped email '{}' to {}", message.subject(), message.to());
		}
	}

	private void sendNow(MailMessage message) {
		try {
			this.sender.send(message);
		}
		catch (RuntimeException ex) {
			// Do not log the message body: it holds the token.
			logger.error("Could not send email '{}' to {}: {}", message.subject(), message.to(), ex.getMessage());
		}
	}

	/** "1 hour", "12 hours", "3 days": whole days are written in days. */
	static String timeLeft(Duration ttl) {
		long hours = Math.max(1, ttl.toHours());
		if (hours % 24 == 0) {
			long days = hours / 24;
			return days + ((days == 1) ? " day" : " days");
		}
		return hours + ((hours == 1) ? " hour" : " hours");
	}

	/** No-break space, so "1 day" stays on one line. */
	private static String noBreak(String text) {
		return text.replace(' ', ' ');
	}

	private static String stripTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

}
