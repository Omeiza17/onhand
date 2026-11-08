package dev.codingstoic.onhand;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class OnhandApplicationTests {

	@Autowired
	RestTestClient client;

	@Autowired
	JdbcClient jdbc;

	@Test
	void healthIsUp() {
		client.get().uri("/actuator/health").exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.status").isEqualTo("UP");
	}

	@Test
	void readinessAndLivenessProbesAreUp() {
		client.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
		client.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
	}

	@Test
	void otherActuatorEndpointsAreNotExposed() {
		client.get().uri("/actuator/env").exchange().expectStatus().isNotFound();
		client.get().uri("/actuator/beans").exchange().expectStatus().isNotFound();
	}

	@Test
	void flywayBaselineApplied() {
		Integer applied = jdbc.sql("select count(*) from flyway_schema_history where version = '1' and success")
				.query(Integer.class).single();
		assertThat(applied).isEqualTo(1);
	}

	@Test
	void unknownPathReturnsProblemDetail() {
		client.get().uri("/api/v1/does-not-exist").exchange()
				.expectStatus().isNotFound()
				.expectHeader().contentTypeCompatibleWith("application/problem+json");
	}

}
