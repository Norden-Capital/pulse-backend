package com.nordencapital.pulse;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Integration tests capture emails instead of sending them (see
 * {@link IntegrationTestBase.CapturingMailSender}).
 */
@TestConfiguration(proxyBeanMethods = false)
class MailCaptureConfiguration {

	@Bean
	@Primary
	IntegrationTestBase.CapturingMailSender capturingMailSender() {
		return new IntegrationTestBase.CapturingMailSender();
	}

}
