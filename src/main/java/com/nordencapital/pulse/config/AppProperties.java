package com.nordencapital.pulse.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * App settings bound from {@code app.*} (see application.yml and .env.example).
 *
 * @param proxySecret shared secret the Vercel route handler sends in the
 * {@code X-Pulse-Proxy-Secret} header; empty turns the check off (local dev only)
 * @param requireProxySecret fail at startup if the secret is empty (the prod profile
 * sets this)
 * @param frontendUrl the website (links in emails, passkey RP ID and origin)
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(String proxySecret, boolean requireProxySecret, @NotBlank String frontendUrl,
		@Valid @NotNull Mail mail, @Valid @NotNull Auth auth) {

	public AppProperties {
		RequiredSetting.check("app.proxy-secret", proxySecret);
		if (requireProxySecret && (proxySecret == null || proxySecret.isBlank())) {
			throw new IllegalStateException("app.proxy-secret is required: set the APP_PROXY_SECRET env var");
		}
		RequiredSetting.check("app.frontend-url", frontendUrl);
	}

	public boolean hasProxySecret() {
		return this.proxySecret != null && !this.proxySecret.isBlank();
	}

	/**
	 * @param from sender, e.g. {@code Pulse <no-reply@nordencapital.ca>}
	 * @param resendApiKey empty = emails are printed to the console (local dev)
	 * @param requireResend fail at startup if the key is empty (the prod profile sets this)
	 * @param async send emails on a background thread (tests turn it off)
	 */
	public record Mail(@NotBlank String from, String resendApiKey, boolean requireResend, boolean async) {

		public Mail {
			RequiredSetting.check("app.mail.from", from);
			RequiredSetting.check("app.mail.resend-api-key", resendApiKey);
		}

		public boolean hasResendApiKey() {
			return this.resendApiKey != null && !this.resendApiKey.isBlank();
		}

	}

	/**
	 * Token lifetimes and rate limits (see docs/api/auth.md §4).
	 */
	public record Auth(@NotNull Duration verifyTtl, @NotNull Duration resetTtl, @Min(1) int loginMaxFailures,
			@NotNull Duration loginLockTime, @Min(1) int forgotPasswordMaxRequests,
			@NotNull Duration forgotPasswordWindow, @Min(1) int registerMaxPerIp, @NotNull Duration registerWindow,
			@Min(1) int verifyMailMaxRequests, @NotNull Duration verifyMailWindow, @Min(1) int mailPerEmailMax,
			@NotNull Duration mailPerEmailWindow) {
	}

}
