package com.nordencapital.pulse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * App settings bound from {@code app.*} (see application.yml and .env.example).
 *
 * @param proxySecret shared secret the Vercel route handler sends in the
 * {@code X-Pulse-Proxy-Secret} header; empty turns the check off (local dev only)
 * @param requireProxySecret fail at startup if the secret is empty (the prod profile
 * sets this)
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String proxySecret, boolean requireProxySecret) {

	public AppProperties {
		RequiredSetting.check("app.proxy-secret", proxySecret);
		if (requireProxySecret && (proxySecret == null || proxySecret.isBlank())) {
			throw new IllegalStateException("app.proxy-secret is required: set the APP_PROXY_SECRET env var");
		}
	}

	public boolean hasProxySecret() {
		return this.proxySecret != null && !this.proxySecret.isBlank();
	}

}
