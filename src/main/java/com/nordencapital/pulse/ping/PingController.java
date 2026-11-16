package com.nordencapital.pulse.ping;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple endpoint the frontend uses to check that it can reach the backend.
 */
@RestController
@RequestMapping("/api/ping")
public class PingController {

	@GetMapping
	public PingResponse ping() {
		return new PingResponse("ok");
	}

	public record PingResponse(String status) {
	}

}
