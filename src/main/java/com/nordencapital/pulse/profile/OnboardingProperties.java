package com.nordencapital.pulse.profile;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings bound from {@code app.onboarding.*} (application.yml).
 *
 * @param planMaxPerIp public plan calculations per client IP in the window (then 429)
 */
@Validated
@ConfigurationProperties(prefix = "app.onboarding")
public record OnboardingProperties(@Min(1) int planMaxPerIp, @NotNull Duration planWindow) {
}
