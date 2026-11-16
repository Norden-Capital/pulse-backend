package com.nordencapital.pulse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Full app + real Postgres (Testcontainers, needs Docker) with the proxy secret check on.
 * All subclasses share one Spring context and one database container.
 */
@SpringBootTest(properties = "app.proxy-secret=" + IntegrationTestBase.PROXY_SECRET)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestBase {

	protected static final String PROXY_SECRET = "test-proxy-secret";

	@Autowired
	protected MockMvcTester mvc;

}
