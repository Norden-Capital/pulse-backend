package com.nordencapital.pulse.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class AppPropertiesTests {

	@Test
	void requiredSecretMustNotBeEmpty() {
		assertThatIllegalStateException().isThrownBy(() -> new AppProperties("", true))
			.withMessageContaining("APP_PROXY_SECRET");
		assertThatIllegalStateException().isThrownBy(() -> new AppProperties(null, true));
		assertThatIllegalStateException().isThrownBy(() -> new AppProperties("   ", true));
	}

	@Test
	void missingEnvVarFailsEvenWhenNotRequired() {
		assertThatIllegalStateException().isThrownBy(() -> new AppProperties("${APP_PROXY_SECRET}", false));
	}

	@Test
	void emptySecretIsAllowedLocally() {
		assertThat(new AppProperties("", false).hasProxySecret()).isFalse();
		assertThat(new AppProperties("s3cret", true).hasProxySecret()).isTrue();
	}

}
