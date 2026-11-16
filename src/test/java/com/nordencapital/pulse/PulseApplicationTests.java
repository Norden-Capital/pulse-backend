package com.nordencapital.pulse;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class PulseApplicationTests extends IntegrationTestBase {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void contextLoadsAndFlywayRanTheBaseline() {
		Integer applied = this.jdbc.queryForObject(
				"select count(*) from flyway_schema_history where version = '1' and success", Integer.class);
		assertThat(applied).isEqualTo(1);
	}

}
