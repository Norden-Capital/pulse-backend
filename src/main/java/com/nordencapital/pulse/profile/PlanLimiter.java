package com.nordencapital.pulse.profile;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.nordencapital.pulse.auth.AttemptLimiter;

/**
 * In-memory limit for the public plan calculation: at most {@code plan-max-per-ip} per
 * client IP in {@code plan-window} (then 429).
 */
@Component
@EnableConfigurationProperties(OnboardingProperties.class)
public class PlanLimiter {

	private final AttemptLimiter limiter;

	public PlanLimiter(OnboardingProperties properties, Clock clock) {
		this.limiter = new AttemptLimiter(properties.planMaxPerIp(), properties.planWindow(), clock);
	}

	/** @return false if this IP is over the limit (nothing is counted) */
	public boolean tryAcquire(String clientIp) {
		return this.limiter.tryAcquire(clientIp);
	}

	/** Forgets all counters (used by tests). */
	public void clear() {
		this.limiter.clear();
	}

}
