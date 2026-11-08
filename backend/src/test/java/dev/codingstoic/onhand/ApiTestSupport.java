package dev.codingstoic.onhand;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Base for API integration tests: full app on a random port against Postgres in
 * Testcontainers (one context and container shared by all subclasses), tables emptied
 * before each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
public abstract class ApiTestSupport {

	@Autowired
	protected RestTestClient client;

	@Autowired
	protected JdbcClient jdbc;

	@BeforeEach
	void cleanDatabase() {
		jdbc.sql("truncate shopping_list_item, stock_event, stock_item, product, location").update();
	}

	protected RestTestClient.ResponseSpec post(String uri, String json) {
		return client.post().uri(uri).contentType(MediaType.APPLICATION_JSON).body(json).exchange();
	}

	protected RestTestClient.ResponseSpec put(String uri, String json) {
		return client.put().uri(uri).contentType(MediaType.APPLICATION_JSON).body(json).exchange();
	}

	/** Pulls a top-level or nested string field out of a JSON response. */
	protected static String field(String json, String name) {
		return json.replaceAll("(?s).*\"" + name + "\":\"([^\"]+)\".*", "$1");
	}

	/** Creates a product and returns its id. */
	protected String createProduct(String json) {
		return field(post("/api/v1/products", json).expectStatus().isCreated()
				.expectBody(String.class).returnResult().getResponseBody(), "id");
	}

	/** Creates a location and returns its id. */
	protected String createLocation(String name) {
		return post("/api/v1/locations", "{\"name\":\"" + name + "\"}")
				.expectStatus().isCreated()
				.expectBody(String.class).returnResult().getResponseBody()
				.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
	}

}
