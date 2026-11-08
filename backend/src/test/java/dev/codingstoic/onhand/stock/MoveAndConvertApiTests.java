package dev.codingstoic.onhand.stock;

import java.math.BigDecimal;

import com.jayway.jsonpath.JsonPath;
import dev.codingstoic.onhand.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/** Edit product: location change = move, and tracking mode / unit changes — all undoable. */
class MoveAndConvertApiTests extends ApiTestSupport {

	private static final String STOCK = "/api/v1/stock";

	private String pantry;

	private String bathroom;

	@BeforeEach
	void setUp() {
		pantry = createLocation("Pantry");
		bathroom = createLocation("Bathroom");
	}

	// --- move ---

	@Test
	void moveTakesTheWholeItemAndTheDefaultPlace() {
		String deo = counted("Deodorant", "piece", pantry);
		String item = add(deo, pantry, "1");

		String json = ok(post(STOCK + "/" + item + "/move", "{\"locationId\":\"%s\"}".formatted(bathroom)));
		assertThat(read(json, "$.item.locationName")).isEqualTo("Bathroom");
		assertThat(read(json, "$.item.id")).isEqualTo(item);
		assertThat(quantity(json)).isEqualByComparingTo("1");
		assertThat(defaultLocation(deo)).isEqualTo(bathroom);

		var event = event(read(json, "$.eventId"));
		assertThat(event.get("type")).isEqualTo("MOVED");
		assertThat(event.get("location_id").toString()).isEqualTo(bathroom);
		assertThat(event.get("from_location_id").toString()).isEqualTo(pantry);
	}

	@Test
	void undoMovesItBackAndRestoresTheDefaultPlace() {
		String deo = counted("Deodorant", "piece", pantry);
		String item = add(deo, pantry, "1");
		String moved = read(ok(post(STOCK + "/" + item + "/move", "{\"locationId\":\"%s\"}".formatted(bathroom))),
				"$.eventId");
		// an edit after the move is kept by the undo
		client.patch().uri("/api/v1/products/" + deo).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.body("{\"minQuantity\":2}").exchange().expectStatus().isOk();

		String json = undo(moved);
		assertThat(read(json, "$.item.locationName")).isEqualTo("Pantry");
		assertThat(defaultLocation(deo)).isEqualTo(pantry);
		assertThat(jdbc.sql("select min_quantity from product where id = ?::uuid").param(deo)
				.query(BigDecimal.class).single()).isEqualByComparingTo("2");
	}

	@Test
	void moveValidation() {
		String deo = counted("Deodorant", "piece", pantry);
		String item = add(deo, pantry, "1");

		post(STOCK + "/" + item + "/move", "{\"locationId\":\"%s\"}".formatted(pantry))
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("locationId");
		post(STOCK + "/" + item + "/move", "{\"locationId\":\"01900000-0000-7000-8000-000000000000\"}")
				.expectStatus().isBadRequest();
		post(STOCK + "/" + item + "/move", "{}").expectStatus().isBadRequest();
		post(STOCK + "/01900000-0000-7000-8000-000000000000/move", "{\"locationId\":\"%s\"}".formatted(bathroom))
				.expectStatus().isNotFound();
	}

	@Test
	void moveIntoAPlaceThatAlreadyHasItIs409() {
		String deo = counted("Deodorant", "piece", pantry);
		String item = add(deo, pantry, "1");
		add(deo, bathroom, "2");

		post(STOCK + "/" + item + "/move", "{\"locationId\":\"%s\"}".formatted(bathroom))
				.expectStatus().isEqualTo(409)
				.expectBody().jsonPath("$.detail").value(String.class, d -> assertThat(d).contains("Bathroom"));
	}

	@Test
	void statusItemsMoveToo() {
		String soap = status("Dish soap", pantry);
		String item = read(ok(post(STOCK, "{\"productId\":\"%s\",\"status\":\"LOW\"}".formatted(soap))), "$.item.id");

		String json = ok(post(STOCK + "/" + item + "/move", "{\"locationId\":\"%s\"}".formatted(bathroom)));
		assertThat(read(json, "$.item.status")).isEqualTo("LOW");
		assertThat(read(undo(read(json, "$.eventId")), "$.item.locationName")).isEqualTo("Pantry");
	}

	@Test
	void changesAtEitherPlaceAfterTheMoveBlockItsUndo() {
		String deo = counted("Deodorant", "piece", pantry);
		String item = add(deo, pantry, "2");
		String moved = read(ok(post(STOCK + "/" + item + "/move", "{\"locationId\":\"%s\"}".formatted(bathroom))),
				"$.eventId");
		// a tap at the new place
		String used = read(ok(client.post().uri(STOCK + "/" + item + "/decrease").exchange()), "$.eventId");

		client.post().uri(STOCK + "/events/" + moved + "/undo").exchange().expectStatus().isEqualTo(409);
		undo(used);
		assertThat(read(undo(moved), "$.item.locationName")).isEqualTo("Pantry");
	}

	@Test
	void theAddBeforeAMoveCantBeUndoneUntilTheMoveIs() {
		String deo = counted("Deodorant", "piece", pantry);
		String added = read(ok(post(STOCK, "{\"productId\":\"%s\",\"quantity\":1}".formatted(deo))), "$.eventId");
		String item = read(ok(client.get().uri(STOCK).exchange()), "$[0].id");
		post(STOCK + "/" + item + "/move", "{\"locationId\":\"%s\"}".formatted(bathroom)).expectStatus().isOk();

		client.post().uri(STOCK + "/events/" + added + "/undo").exchange().expectStatus().isEqualTo(409);
	}

	// --- tracking mode / unit ---

	@Test
	void countedToStatusIsHaveWhenSomeIsLeftAndUndoRestoresEverything() {
		String wine = counted("Red wine", "bottle", pantry);
		jdbc.sql("update product set min_quantity = 1, step_size = 2 where id = ?::uuid").param(wine).update();
		String item = add(wine, pantry, "2");

		String json = ok(post(STOCK + "/" + item + "/convert", "{\"trackingMode\":\"STATUS\"}"));
		assertThat(read(json, "$.item.status")).isEqualTo("HAVE");
		assertThat(read(json, "$.item.quantity")).isNull();
		assertThat(read(json, "$.item.trackingMode")).isEqualTo("STATUS");
		var event = event(read(json, "$.eventId"));
		assertThat(event.get("type")).isEqualTo("STATUS_CHANGED");
		assertThat(event.get("product_before").toString()).contains("bottle");

		json = undo(read(json, "$.eventId"));
		assertThat(quantity(json)).isEqualByComparingTo("2");
		assertThat(read(json, "$.item.unit")).isEqualTo("bottle");
		assertThat(read(json, "$.item.trackingMode")).isEqualTo("COUNTED");
		var product = jdbc.sql("select min_quantity, step_size from product where id = ?::uuid").param(wine)
				.query().singleRow();
		assertThat((BigDecimal) product.get("min_quantity")).isEqualByComparingTo("1");
		assertThat((BigDecimal) product.get("step_size")).isEqualByComparingTo("2");
	}

	@Test
	void countedToStatusIsOutWhenNoneIsLeft() {
		String wine = counted("Red wine", "bottle", pantry);
		String item = add(wine, pantry, "0");

		assertThat(read(ok(post(STOCK + "/" + item + "/convert", "{\"trackingMode\":\"STATUS\"}")), "$.item.status"))
				.isEqualTo("OUT");
	}

	@Test
	void statusToCountedTakesTheAmountAndUndoRestoresTheStatus() {
		String soap = status("Dish soap", pantry);
		String item = read(ok(post(STOCK, "{\"productId\":\"%s\",\"status\":\"LOW\"}".formatted(soap))), "$.item.id");

		String json = ok(post(STOCK + "/" + item + "/convert",
				"{\"trackingMode\":\"COUNTED\",\"unit\":\"bottle\",\"quantity\":1}"));
		assertThat(quantity(json)).isEqualByComparingTo("1");
		assertThat(read(json, "$.item.unit")).isEqualTo("bottle");
		assertThat(read(json, "$.item.status")).isNull();
		assertThat(event(read(json, "$.eventId")).get("type")).isEqualTo("ADJUSTED");

		json = undo(read(json, "$.eventId"));
		assertThat(read(json, "$.item.status")).isEqualTo("LOW");
		assertThat(read(json, "$.item.trackingMode")).isEqualTo("STATUS");
		assertThat(read(json, "$.item.unit")).isNull();
	}

	@Test
	void unitChangeTakesTheNewAmountAndUndoRestoresUnitAmountAndStep() {
		String wine = counted("Red wine", "bottle", pantry);
		jdbc.sql("update product set step_size = 2 where id = ?::uuid").param(wine).update();
		String item = add(wine, pantry, "2");

		String json = ok(post(STOCK + "/" + item + "/convert",
				"{\"trackingMode\":\"COUNTED\",\"unit\":\"ml\",\"quantity\":1500}"));
		assertThat(quantity(json)).isEqualByComparingTo("1500");
		assertThat(read(json, "$.item.unit")).isEqualTo("ml");
		// the step starts fresh: ml default
		assertThat(new BigDecimal(read(json, "$.item.stepSize"))).isEqualByComparingTo("250");

		json = undo(read(json, "$.eventId"));
		assertThat(quantity(json)).isEqualByComparingTo("2");
		assertThat(read(json, "$.item.unit")).isEqualTo("bottle");
		assertThat(new BigDecimal(read(json, "$.item.stepSize"))).isEqualByComparingTo("2");
	}

	@Test
	void convertValidation() {
		String wine = counted("Red wine", "bottle", pantry);
		String item = add(wine, pantry, "2");
		String soap = status("Dish soap", pantry);
		String soapItem = read(ok(post(STOCK, "{\"productId\":\"%s\"}".formatted(soap))), "$.item.id");

		// unit change needs the amount; same unit is no change
		post(STOCK + "/" + item + "/convert", "{\"trackingMode\":\"COUNTED\",\"unit\":\"ml\"}")
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("quantity");
		post(STOCK + "/" + item + "/convert", "{\"trackingMode\":\"COUNTED\",\"unit\":\"bottle\",\"quantity\":1}")
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("unit");
		// already STATUS; STATUS → COUNTED needs the amount
		post(STOCK + "/" + soapItem + "/convert", "{\"trackingMode\":\"STATUS\"}")
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("trackingMode");
		post(STOCK + "/" + soapItem + "/convert", "{\"trackingMode\":\"COUNTED\"}")
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("quantity");
		post(STOCK + "/" + item + "/convert", "{\"trackingMode\":\"COUNTED\",\"unit\":\"ml\",\"quantity\":-1}")
				.expectStatus().isBadRequest();
		assertThat(jdbc.sql("select count(*) from stock_event").query(Integer.class).single()).isEqualTo(2);
	}

	@Test
	void modeOrUnitChangeIsRefusedWhileInTwoPlaces() {
		String wine = counted("Red wine", "bottle", pantry);
		String item = add(wine, pantry, "2");
		add(wine, bathroom, "1");

		post(STOCK + "/" + item + "/convert", "{\"trackingMode\":\"STATUS\"}")
				.expectStatus().isEqualTo(409)
				.expectBody().jsonPath("$.detail").value(String.class, d -> assertThat(d).contains("2 places"));
	}

	// --- remove an item after a move: its history goes, and a fresh add starts clean ---

	@Test
	void removingAMovedItemClearsItsHistoryEverywhere() {
		String deo = counted("Deodorant", "piece", pantry);
		String item = add(deo, pantry, "1");
		post(STOCK + "/" + item + "/move", "{\"locationId\":\"%s\"}".formatted(bathroom)).expectStatus().isOk();

		client.delete().uri(STOCK + "/" + item).exchange().expectStatus().isNoContent();
		assertThat(jdbc.sql("select count(*) from stock_event").query(Integer.class).single()).isZero();

		String added = read(ok(post(STOCK, "{\"productId\":\"%s\",\"locationId\":\"%s\",\"quantity\":1}"
				.formatted(deo, pantry))), "$.eventId");
		assertThat(read(undo(added), "$.item")).isNull();
	}

	// --- helpers ---

	private String counted(String name, String unit, String location) {
		return createProduct("{\"name\":\"%s\",\"trackingMode\":\"COUNTED\",\"defaultUnit\":\"%s\",\"defaultLocationId\":\"%s\"}"
				.formatted(name, unit, location));
	}

	private String status(String name, String location) {
		return createProduct("{\"name\":\"%s\",\"trackingMode\":\"STATUS\",\"defaultLocationId\":\"%s\"}"
				.formatted(name, location));
	}

	private String add(String product, String location, String quantity) {
		return read(ok(post(STOCK, "{\"productId\":\"%s\",\"locationId\":\"%s\",\"quantity\":%s}"
				.formatted(product, location, quantity))), "$.item.id");
	}

	private String undo(String eventId) {
		return ok(client.post().uri(STOCK + "/events/" + eventId + "/undo").exchange());
	}

	private String defaultLocation(String product) {
		return jdbc.sql("select default_location_id::text from product where id = ?::uuid").param(product)
				.query(String.class).single();
	}

	private java.util.Map<String, Object> event(String id) {
		return jdbc.sql("select type, location_id, from_location_id, product_before::text as product_before from stock_event where id = ?::uuid")
				.param(id).query().singleRow();
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

}
