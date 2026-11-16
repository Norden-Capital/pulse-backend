package com.nordencapital.pulse.passkey;

import java.net.URI;
import java.util.Set;

import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.AttestationConveyancePreference;
import com.yubico.webauthn.data.RelyingPartyIdentity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.nordencapital.pulse.config.AppProperties;

/**
 * WebAuthn "relying party" = the Pulse website. Everything comes from APP_FRONTEND_URL
 * (no extra setting):
 * <ul>
 * <li>RP ID = its host (the Vercel host in prod, localhost locally);</li>
 * <li>allowed origin = its scheme + host + port. On localhost any port is accepted, so
 * several local frontends (3000, 3001) work.</li>
 * </ul>
 * The browser talks to the frontend origin; the frontend proxies /api to us. When the
 * domain changes, passkeys made for the old host stop working and users add them again.
 */
@Configuration(proxyBeanMethods = false)
class PasskeyConfig {

	static final String RP_NAME = "Pulse";

	@Bean
	RelyingParty relyingParty(PasskeyCredentials credentials, AppProperties properties) {
		URI frontend = URI.create(properties.frontendUrl());
		String host = frontend.getHost();
		if (host == null || frontend.getScheme() == null) {
			throw new IllegalStateException("APP_FRONTEND_URL must be a full URL like https://example.com");
		}
		String origin = frontend.getScheme() + "://" + frontend.getAuthority();
		return RelyingParty.builder()
			.identity(RelyingPartyIdentity.builder().id(host).name(RP_NAME).build())
			.credentialRepository(credentials)
			.origins(Set.of(origin))
			.allowOriginPort("localhost".equals(host))
			.attestationConveyancePreference(AttestationConveyancePreference.NONE)
			.build();
	}

}
