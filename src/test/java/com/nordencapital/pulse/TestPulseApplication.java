package com.nordencapital.pulse;

import org.springframework.boot.SpringApplication;

/**
 * Runs the app against a throwaway Testcontainers Postgres (no compose.yaml needed).
 */
public class TestPulseApplication {

	public static void main(String[] args) {
		SpringApplication.from(PulseApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
