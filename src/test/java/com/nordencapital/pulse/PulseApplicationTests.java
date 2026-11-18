package com.nordencapital.pulse;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PulseApplicationTests extends IntegrationTestBase {

	@Test
	void contextLoadsAndFlywayRanAllMigrations() {
		assertThat(this.jdbc.queryForList(
				"SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class))
			.containsExactly("1", "2", "3", "4", "5", "6");
	}

}
