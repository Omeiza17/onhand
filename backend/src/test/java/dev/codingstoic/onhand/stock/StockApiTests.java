package dev.codingstoic.onhand.stock;

import java.math.BigDecimal;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import dev.codingstoic.onhand.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

class StockApiTests extends ApiTestSupport {

	private static final String STOCK = "/api/v1/stock";

	private String pantry;

	private String fridge;

	private String rice;

	private String cumin;

	@BeforeEach
	void setUp() {
		pantry = createLocation("Pantry");
		fridge = createLocation("Fridge");
		rice = createProduct("""
				{"name":"Basmati rice","trackingMode":"COUNTED","defaultUnit":"kg","defaultLocationId":"%s"}
				""".formatted(pantry));
		cumin = createProduct("{\"name\":\"Cumin\",\"trackingMode\":\"STATUS\",\"category\":\"Spices\"}");
	}

	// --- add ---

	@Test
	void addCountedCreatesItemAtDefaultLocationWithAddedEvent() {
		String json = ok(post(STOCK, "{\"productId\":\"%s\",\"quantity\":2}".formatted(rice)));

		assertThat(read(json, "$.item.productName")).isEqualTo("Basmati rice");
		assertThat(read(json, "$.item.locationName")).isEqualTo("Pantry");
		assertThat(read(json, "$.item.unit")).isEqualTo("kg");
		assertThat(quantity(json)).isEqualByComparingTo("2");
		assertThat(read(json, "$.item.status")).isNull();
		assertEvent(read(json, "$.eventId"), "ADDED", "2", null, null);
	}

	@Test
	void addingAgainIncreasesTheSameItem() {
		post(STOCK, "{\"productId\":\"%s\"}".formatted(rice)).expectStatus().isOk();
		String json = ok(post(STOCK, "{\"productId\":\"%s\",\"quantity\":1.5}".formatted(rice)));

		assertThat(quantity(json)).isEqualByComparingTo("2.5");
		assertThat(count("stock_item")).isEqualTo(1);
		assertThat(count("stock_event")).isEqualTo(2);
	}

	@Test
	void addStatusSetsHave() {
		String json = ok(post(STOCK, "{\"productId\":\"%s\",\"locationId\":\"%s\"}".formatted(cumin, pantry)));

		assertThat(read(json, "$.item.status")).isEqualTo("HAVE");
		assertThat(read(json, "$.item.quantity")).isNull();
		assertThat(read(json, "$.item.trackingMode")).isEqualTo("STATUS");
		assertEvent(read(json, "$.eventId"), "ADDED", null, null, "HAVE");
	}

	@Test
	void addValidation() {
		// no default location and none given
		post(STOCK, "{\"productId\":\"%s\"}".formatted(cumin))
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("locationId");
		// quantity on a STATUS product
		post(STOCK, "{\"productId\":\"%s\",\"locationId\":\"%s\",\"quantity\":1}".formatted(cumin, pantry))
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("quantity");
		// unknown product, unknown location, non-positive quantity, missing productId
		post(STOCK, "{\"productId\":\"01900000-0000-7000-8000-000000000000\"}")
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("productId");
		post(STOCK, "{\"productId\":\"%s\",\"locationId\":\"01900000-0000-7000-8000-000000000000\"}".formatted(rice))
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("locationId");
		post(STOCK, "{\"productId\":\"%s\",\"quantity\":0}".formatted(rice))
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("quantity");
		post(STOCK, "{}").expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("productId");
		assertThat(count("stock_item")).isZero();
		assertThat(count("stock_event")).isZero();
	}

	// --- list ---

	@Test
	void listIsGroupedByLocationOrderThenProductName() {
		String eggs = createProduct("{\"name\":\"eggs\",\"trackingMode\":\"COUNTED\"}");
		post(STOCK, "{\"productId\":\"%s\",\"locationId\":\"%s\"}".formatted(eggs, fridge)).expectStatus().isOk();
		post(STOCK, "{\"productId\":\"%s\",\"locationId\":\"%s\"}".formatted(cumin, pantry)).expectStatus().isOk();
		post(STOCK, "{\"productId\":\"%s\"}".formatted(rice)).expectStatus().isOk();

		client.get().uri(STOCK).exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[*].productName").isEqualTo(List.of("Basmati rice", "Cumin", "eggs"))
				.jsonPath("$[*].locationName").isEqualTo(List.of("Pantry", "Pantry", "Fridge"))
				.jsonPath("$[1].category").isEqualTo("Spices")
				.jsonPath("$[0].updatedAt").exists();
	}

	@Test
	void listFiltersByKindAndEachItemCarriesKind() {
		String toiletPaper = createProduct("""
				{"name":"Toilet paper","kind":"HOUSEHOLD","trackingMode":"COUNTED","defaultUnit":"pack"}
				""");
		String bathroom = createLocation("Bathroom");
		post(STOCK, "{\"productId\":\"%s\",\"locationId\":\"%s\",\"quantity\":6}".formatted(toiletPaper, bathroom))
				.expectStatus().isOk();
		post(STOCK, "{\"productId\":\"%s\",\"locationId\":\"%s\"}".formatted(cumin, pantry)).expectStatus().isOk();
		post(STOCK, "{\"productId\":\"%s\"}".formatted(rice)).expectStatus().isOk();

		// no filter = All
		client.get().uri(STOCK).exchange()
				.expectBody()
				.jsonPath("$[*].productName").isEqualTo(List.of("Basmati rice", "Cumin", "Toilet paper"))
				.jsonPath("$[*].kind").isEqualTo(List.of("FOOD", "FOOD", "HOUSEHOLD"));

		// Food keeps location grouping and name order
		client.get().uri(STOCK + "?kind=FOOD").exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[*].productName").isEqualTo(List.of("Basmati rice", "Cumin"))
				.jsonPath("$[*].locationName").isEqualTo(List.of("Pantry", "Pantry"));

		client.get().uri(STOCK + "?kind=HOUSEHOLD").exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.length()").isEqualTo(1)
				.jsonPath("$[0].productName").isEqualTo("Toilet paper")
				.jsonPath("$[0].locationName").isEqualTo("Bathroom")
				.jsonPath("$[0].kind").isEqualTo("HOUSEHOLD");
	}

	@Test
	void kindFilterWithNoMatchesIsEmptyAndUnknownKindIs400() {
		post(STOCK, "{\"productId\":\"%s\"}".formatted(rice)).expectStatus().isOk();

		client.get().uri(STOCK + "?kind=HOUSEHOLD").exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.length()").isEqualTo(0);
		client.get().uri(STOCK + "?kind=DURABLE").exchange()
				.expectStatus().isBadRequest()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
	}

	// --- quick update ---

	@Test
	void increaseAndDecreaseByOneTapAndByAmount() {
		String item = addRice("3");

		String json = ok(client.post().uri(STOCK + "/" + item + "/increase").exchange());
		assertThat(quantity(json)).isEqualByComparingTo("4");
		assertEvent(read(json, "$.eventId"), "ADDED", "1", null, null);

		json = ok(client.post().uri(STOCK + "/" + item + "/decrease").exchange());
		assertThat(quantity(json)).isEqualByComparingTo("3");
		assertEvent(read(json, "$.eventId"), "USED", "-1", null, null);

		json = ok(post(STOCK + "/" + item + "/decrease", "{\"amount\":0.5,\"note\":\"dinner\"}"));
		assertThat(quantity(json)).isEqualByComparingTo("2.5");
		assertThat(jdbc.sql("select note from stock_event where id = ?::uuid")
				.param(read(json, "$.eventId")).query(String.class).single()).isEqualTo("dinner");
	}

	@Test
	void decreaseStopsAtZeroThenIs409() {
		String item = addRice("1");

		String json = ok(post(STOCK + "/" + item + "/decrease", "{\"amount\":5}"));
		assertThat(quantity(json)).isEqualByComparingTo("0");
		// the event records what was actually used
		assertEvent(read(json, "$.eventId"), "USED", "-1", null, null);

		client.post().uri(STOCK + "/" + item + "/decrease").exchange()
				.expectStatus().isEqualTo(409)
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(count("stock_event")).isEqualTo(2);
	}

	@Test
	void setQuantityWritesAdjustedWithTheDifference() {
		String item = addRice("2");

		String json = ok(put(STOCK + "/" + item + "/quantity", "{\"quantity\":5.25}"));
		assertThat(quantity(json)).isEqualByComparingTo("5.25");
		assertEvent(read(json, "$.eventId"), "ADJUSTED", "3.25", null, null);

		// same value: nothing changes, no event
		json = ok(put(STOCK + "/" + item + "/quantity", "{\"quantity\":5.25}"));
		assertThat(read(json, "$.eventId")).isNull();
		assertThat(count("stock_event")).isEqualTo(2);

		put(STOCK + "/" + item + "/quantity", "{\"quantity\":-1}").expectStatus().isBadRequest();
		put(STOCK + "/" + item + "/quantity", "{}").expectStatus().isBadRequest();
	}

	@Test
	void cycleGoesHaveLowOutHave() {
		String item = addCumin();

		for (String[] step : new String[][] { { "HAVE", "LOW" }, { "LOW", "OUT" }, { "OUT", "HAVE" } }) {
			String json = ok(client.post().uri(STOCK + "/" + item + "/cycle").exchange());
			assertThat(read(json, "$.item.status")).isEqualTo(step[1]);
			assertEvent(read(json, "$.eventId"), "STATUS_CHANGED", null, step[0], step[1]);
		}
	}

	@Test
	void wrongActionForTrackingModeIs400() {
		String riceItem = addRice("1");
		String cuminItem = addCumin();

		client.post().uri(STOCK + "/" + riceItem + "/cycle").exchange().expectStatus().isBadRequest();
		client.post().uri(STOCK + "/" + cuminItem + "/increase").exchange().expectStatus().isBadRequest();
		client.post().uri(STOCK + "/" + cuminItem + "/decrease").exchange().expectStatus().isBadRequest();
		put(STOCK + "/" + cuminItem + "/quantity", "{\"quantity\":1}").expectStatus().isBadRequest();
		assertThat(count("stock_event")).isEqualTo(2);
	}

	@Test
	void unknownItemIs404() {
		String missing = STOCK + "/01900000-0000-7000-8000-000000000000";
		client.post().uri(missing + "/increase").exchange().expectStatus().isNotFound();
		client.post().uri(missing + "/cycle").exchange().expectStatus().isNotFound();
		client.post().uri(STOCK + "/events/01900000-0000-7000-8000-000000000000/undo").exchange()
				.expectStatus().isNotFound();
	}

	// --- undo ---

	@Test
	void undoReversesQuantityChangesInOrder() {
		String item = addRice("2");
		String inc = read(ok(post(STOCK + "/" + item + "/increase", "{\"amount\":3}")), "$.eventId");
		String dec = read(ok(client.post().uri(STOCK + "/" + item + "/decrease").exchange()), "$.eventId");
		String adj = read(ok(put(STOCK + "/" + item + "/quantity", "{\"quantity\":10}")), "$.eventId");

		assertThat(quantity(undo(adj))).isEqualByComparingTo("4");
		assertThat(quantity(undo(dec))).isEqualByComparingTo("5");
		assertThat(quantity(undo(inc))).isEqualByComparingTo("2");
		// undone events stay in the log, marked
		assertThat(count("stock_event")).isEqualTo(4);
		assertThat(jdbc.sql("select count(*) from stock_event where undone_at is not null")
				.query(Integer.class).single()).isEqualTo(3);
	}

	@Test
	void undoRestoresPreviousStatus() {
		String item = addCumin();
		client.post().uri(STOCK + "/" + item + "/cycle").exchange().expectStatus().isOk();
		String toOut = read(ok(client.post().uri(STOCK + "/" + item + "/cycle").exchange()), "$.eventId");

		assertThat(read(undo(toOut), "$.item.status")).isEqualTo("LOW");
	}

	@Test
	void undoingTheEventThatCreatedTheItemRemovesIt() {
		String json = ok(post(STOCK, "{\"productId\":\"%s\",\"locationId\":\"%s\"}".formatted(cumin, pantry)));

		String undone = undo(read(json, "$.eventId"));
		assertThat(read(undone, "$.item")).isNull();
		assertThat(count("stock_item")).isZero();

		// adding again starts fresh
		post(STOCK, "{\"productId\":\"%s\",\"locationId\":\"%s\"}".formatted(cumin, pantry)).expectStatus().isOk();
		assertThat(count("stock_item")).isEqualTo(1);
	}

	@Test
	void undoOfAddToExistingStatusItemRestoresPreviousStatus() {
		String item = addCumin();
		client.post().uri(STOCK + "/" + item + "/cycle").exchange().expectStatus().isOk(); // LOW
		String added = read(ok(post(STOCK, "{\"productId\":\"%s\",\"locationId\":\"%s\"}".formatted(cumin, pantry))),
				"$.eventId"); // back to HAVE

		assertThat(read(undo(added), "$.item.status")).isEqualTo("LOW");
	}

	@Test
	void undoOfAnOlderChangeIs409() {
		String item = addRice("2");
		String first = read(ok(client.post().uri(STOCK + "/" + item + "/increase").exchange()), "$.eventId");
		client.post().uri(STOCK + "/" + item + "/increase").exchange().expectStatus().isOk();

		client.post().uri(STOCK + "/events/" + first + "/undo").exchange()
				.expectStatus().isEqualTo(409)
				.expectBody().jsonPath("$.detail").value(String.class, d -> assertThat(d).contains("latest"));
		assertThat(jdbc.sql("select quantity from stock_item where id = ?::uuid").param(item)
				.query(BigDecimal.class).single()).isEqualByComparingTo("4");
	}

	@Test
	void undoTwiceIs409() {
		String item = addRice("2");
		String inc = read(ok(client.post().uri(STOCK + "/" + item + "/increase").exchange()), "$.eventId");

		undo(inc);
		client.post().uri(STOCK + "/events/" + inc + "/undo").exchange()
				.expectStatus().isEqualTo(409)
				.expectBody().jsonPath("$.detail").value(String.class, d -> assertThat(d).contains("already"));
	}

	@Test
	void changesToOtherItemsDoNotBlockUndo() {
		String riceItem = addRice("2");
		String inc = read(ok(client.post().uri(STOCK + "/" + riceItem + "/increase").exchange()), "$.eventId");
		addCumin();

		assertThat(quantity(undo(inc))).isEqualByComparingTo("2");
	}

	// --- removing a stock item ---

	@Test
	void removeDeletesTheItemAndItsHistoryOnly() {
		String riceItem = addRice("2");
		client.post().uri(STOCK + "/" + riceItem + "/decrease").exchange().expectStatus().isOk();
		addCumin();

		client.delete().uri(STOCK + "/" + riceItem).exchange().expectStatus().isNoContent();

		assertThat(count("stock_item")).isEqualTo(1);
		// rice's two events are gone, cumin's one remains
		assertThat(count("stock_event")).isEqualTo(1);
		client.delete().uri(STOCK + "/" + riceItem).exchange().expectStatus().isNotFound();
	}

	@Test
	void usedUpItemCanBeRemovedSoItsLocationCanBeDeleted() {
		String item = addRice("1");
		client.post().uri(STOCK + "/" + item + "/decrease").exchange().expectStatus().isOk();
		client.delete().uri("/api/v1/locations/" + pantry).exchange()
				.expectStatus().isEqualTo(409)
				.expectBody().jsonPath("$.detail").value(String.class, d -> assertThat(d).contains("remove them"));

		client.delete().uri(STOCK + "/" + item).exchange().expectStatus().isNoContent();
		client.delete().uri("/api/v1/locations/" + pantry).exchange().expectStatus().isNoContent();
	}

	@Test
	void afterRemovalAddingStartsAFreshItemWhoseUndoRemovesIt() {
		String item = addRice("2");
		client.delete().uri(STOCK + "/" + item).exchange().expectStatus().isNoContent();

		String added = read(ok(post(STOCK, "{\"productId\":\"%s\",\"quantity\":3}".formatted(rice))), "$.eventId");
		assertThat(read(undo(added), "$.item")).isNull();
		assertThat(count("stock_item")).isZero();
	}

	// --- deleting things that have stock ---

	@Test
	void locationWithStockCannotBeDeleted() {
		addRice("1");

		client.delete().uri("/api/v1/locations/" + pantry).exchange()
				.expectStatus().isEqualTo(409)
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		// a location without stock still can
		client.delete().uri("/api/v1/locations/" + fridge).exchange().expectStatus().isNoContent();
	}

	@Test
	void deletingAProductRemovesItsStockAndHistory() {
		addRice("1");

		client.delete().uri("/api/v1/products/" + rice).exchange().expectStatus().isNoContent();
		assertThat(count("stock_item")).isZero();
		assertThat(count("stock_event")).isZero();
	}

	@Test
	void trackingModeCannotChangeWhileProductHasStock() {
		addRice("1");

		put("/api/v1/products/" + rice, "{\"name\":\"Basmati rice\",\"trackingMode\":\"STATUS\"}")
				.expectStatus().isEqualTo(409);
		// other edits are fine
		put("/api/v1/products/" + rice, "{\"name\":\"White basmati\",\"trackingMode\":\"COUNTED\"}")
				.expectStatus().isOk();
	}

	// --- helpers ---

	private String addRice(String quantity) {
		return read(ok(post(STOCK, "{\"productId\":\"%s\",\"quantity\":%s}".formatted(rice, quantity))), "$.item.id");
	}

	private String addCumin() {
		return read(ok(post(STOCK, "{\"productId\":\"%s\",\"locationId\":\"%s\"}".formatted(cumin, pantry))), "$.item.id");
	}

	private String undo(String eventId) {
		return ok(client.post().uri(STOCK + "/events/" + eventId + "/undo").exchange());
	}

	private static String ok(RestTestClient.ResponseSpec response) {
		return response.expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
	}

	private static String read(String json, String path) {
		Object value = JsonPath.read(json, path);
		return value == null ? null : value.toString();
	}

	private static BigDecimal quantity(String json) {
		return new BigDecimal(read(json, "$.item.quantity"));
	}

	private int count(String table) {
		return jdbc.sql("select count(*) from " + table).query(Integer.class).single();
	}

	private void assertEvent(String eventId, String type, String delta, String statusFrom, String statusTo) {
		var row = jdbc.sql("""
				select type, quantity_delta, status_from, status_to, undone_at
				from stock_event where id = ?::uuid
				""").param(eventId).query().singleRow();
		assertThat(row.get("type")).isEqualTo(type);
		if (delta == null) {
			assertThat(row.get("quantity_delta")).isNull();
		}
		else {
			assertThat((BigDecimal) row.get("quantity_delta")).isEqualByComparingTo(delta);
		}
		assertThat(row.get("status_from")).isEqualTo(statusFrom);
		assertThat(row.get("status_to")).isEqualTo(statusTo);
		assertThat(row.get("undone_at")).isNull();
	}

}
