package com.nordencapital.pulse;

import org.junit.jupiter.api.Test;

import com.nordencapital.pulse.config.ProxySecretFilter;

import static org.assertj.core.api.Assertions.assertThat;

class ApiIntegrationTests extends IntegrationTestBase {

	private static final String FORBIDDEN = "{\"error\":\"forbidden\",\"message\":\"Access denied\"}";

	@Test
	void pingWithTheRightSecretAnswersOk() {
		assertThat(this.mvc.get().uri("/api/ping").header(ProxySecretFilter.HEADER, PROXY_SECRET)).hasStatusOk()
			.bodyJson()
			.isStrictlyEqualTo("{\"status\":\"ok\"}");
	}

	@Test
	void pingWithoutTheSecretIsForbidden() {
		assertThat(this.mvc.get().uri("/api/ping")).hasStatus(403).bodyJson().isStrictlyEqualTo(FORBIDDEN);
	}

	@Test
	void pingWithAWrongSecretIsForbidden() {
		assertThat(this.mvc.get().uri("/api/ping").header(ProxySecretFilter.HEADER, "wrong")).hasStatus(403)
			.bodyJson()
			.isStrictlyEqualTo(FORBIDDEN);
		assertThat(this.mvc.get().uri("/api/ping").header(ProxySecretFilter.HEADER, PROXY_SECRET + "x"))
			.hasStatus(403);
	}

	@Test
	void unknownApiPathIsForbiddenWithoutTheSecret() {
		assertThat(this.mvc.get().uri("/api/does-not-exist")).hasStatus(403);
	}

	@Test
	void unknownApiPathGivesJsonNotFoundWithTheSecret() {
		assertThat(this.mvc.get().uri("/api/does-not-exist").header(ProxySecretFilter.HEADER, PROXY_SECRET))
			.hasStatus(404)
			.bodyJson()
			.isStrictlyEqualTo("{\"error\":\"not_found\",\"message\":\"Not Found\"}");
	}

	@Test
	void wrongMethodGivesJsonMethodNotAllowed() {
		assertThat(this.mvc.post().uri("/api/ping").header(ProxySecretFilter.HEADER, PROXY_SECRET)).hasStatus(405)
			.bodyJson()
			.isStrictlyEqualTo("{\"error\":\"method_not_allowed\",\"message\":\"Method Not Allowed\"}");
	}

	@Test
	void healthProbesNeedNoSecret() {
		assertThat(this.mvc.get().uri("/actuator/health")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("UP");
		assertThat(this.mvc.get().uri("/actuator/health/liveness")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("UP");
		assertThat(this.mvc.get().uri("/actuator/health/readiness")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("UP");
	}

	@Test
	void onlyHealthIsExposed() {
		assertThat(this.mvc.get().uri("/actuator/info")).hasStatus(404);
		assertThat(this.mvc.get().uri("/actuator/env")).hasStatus(404);
	}

}
