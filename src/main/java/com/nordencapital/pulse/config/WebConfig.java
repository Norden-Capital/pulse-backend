package com.nordencapital.pulse.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppProperties.class)
public class WebConfig {

	private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

	@Bean
	FilterRegistrationBean<ProxySecretFilter> proxySecretFilter(AppProperties properties) {
		if (!properties.hasProxySecret()) {
			log.warn("APP_PROXY_SECRET is empty: the proxy secret check on /api/** is OFF (local dev only)");
		}
		FilterRegistrationBean<ProxySecretFilter> registration = new FilterRegistrationBean<>(
				new ProxySecretFilter(properties.proxySecret()));
		// Only the API. Actuator (/actuator/health/**) stays open for the container health check.
		registration.addUrlPatterns("/api/*");
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
		return registration;
	}

}
