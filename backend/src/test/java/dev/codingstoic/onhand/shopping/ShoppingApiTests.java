package dev.codingstoic.onhand.shopping;

import java.math.BigDecimal;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import dev.codingstoic.onhand.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

class ShoppingApiTests extends ApiTestSupport {

	private static final String SHOPPING = "/api/v1/shopping";

	private String pantry;

	private String fridge;

	private String sink;

	@BeforeEach
	void setUp() {
		pantry = createLocation("Pantry");
		fridge = createLocation("Fridge");
		sink = createLocation("Under the sink");
	}

	// --- running low at home (derived from stock) ---

	@Test
	void statusProductsThatAreLowOrOutAreListed() {
		statusAt("Olive oil", pantry, "LOW");
		statusAt("Spinach", fridge, "OUT");
		statusAt("Cumin", pantry, "HAVE");

		String json = list();
		assertThat(names(json)).containsExactly("Olive oil", "Spinach");
		assertThat(read(json, "$.items[0].reason")).isEqualTo("LOW");
		assertThat(read(json, "$.items[0].places")).isEqualTo("Pantry");
		assertThat(read(json, "$.items[0].source")).isEqualTo("AUTO");
		assertThat(read(json, "$.items[1].reason")).isEqualTo("OUT");
		assertThat(read(json, "$.toBuy")).isEqualTo("2");
	}

	@Test
	void countedProductsBelowTheirMinimumAreListed() {
		countedAt("Floor cleaner", sink, "piece", "1", "0");
		countedAt("Eggs", fridge, "piece", "4", "4"); // at the minimum: not below
		countedAt("Rice", pantry, "kg", null, "0"); // no minimum: never listed

		String json = list();
		assertThat(names(json)).containsExactly("Floor cleaner");
		assertThat(read(json, "$.items[0].reason")).isEqualTo("BELOW_MIN");
		assertThat(new BigDecimal(read(json, "$.items[0].minQuantity"))).isEqualByComparingTo("1");
		assertThat(read(json, "$.items[0].unit")).isEqualTo("piece");
	}

	@Test
	void countedTotalsAcrossPlacesAndArchivedProductsAreLeftOut() {
		String rice = countedAt("Rice", pantry, "kg", "1", "0.5");
		post("/api/v1/stock", "{\"productId\":\"%s\",\"locationId\":\"%s\",\"quantity\":0.5}".formatted(rice, fridge))
				.expectStatus().isOk();
		String oil = statusAt("Olive oil", pantry, "OUT");
		client.post().uri("/api/v1/products/" + oil + "/archive").exchange().expectStatus().isOk();

		// 0.5 + 0.5 = 1 kg: not below; oil archived
		assertThat(names(list())).isEmpty();
	}

	@Test
	void restockingOnHomeTakesItOffTheList() {
		String oil = statusAt("Olive oil", pantry, "LOW");
		check("products/" + oil, true);

		post("/api/v1/stock", "{\"productId\":\"%s\",\"locationId\":\"%s\"}".formatted(oil, pantry))
				.expectStatus().isOk(); // back to HAVE
		assertThat(names(list())).isEmpty();
		// running low again later: not still ticked from before
		cycleTo(oil, pantry, "LOW");
		assertThat(read(list(), "$.items[0].checked")).isEqualTo("false");
	}

	// --- ticking ---

	@Test
	void tickingMovesItIntoTheBasket() {
		String oil = statusAt("Olive oil", pantry, "LOW");
		statusAt("Butter", fridge, "LOW");

		String json = check("products/" + oil, true);
		assertThat(names(json)).containsExactly("Butter", "Olive oil");
		assertThat(read(json, "$.items[1].checked")).isEqualTo("true");
		assertThat(read(json, "$.toBuy")).isEqualTo("1");
		assertThat(read(json, "$.inBasket")).isEqualTo("1");

		json = check("products/" + oil, false);
		assertThat(read(json, "$.inBasket")).isEqualTo("0");
	}

	@Test
	void tickingAProductThatIsntLowIs409() {
		String cumin = statusAt("Cumin", pantry, "HAVE");
		put(SHOPPING + "/products/" + cumin + "/checked", "{\"checked\":true}").expectStatus().isEqualTo(409);
		put(SHOPPING + "/products/01900000-0000-7000-8000-000000000000/checked", "{\"checked\":true}")
				.expectStatus().isNotFound();
		put(SHOPPING + "/products/" + cumin + "/checked", "{}").expectStatus().isBadRequest();
	}

	// --- added by you ---

	@Test
	void freeTextItemsAreAddedAndTicked() {
		String json = ok(post(SHOPPING + "/items", "{\"name\":\" Bread \"}"), 201);
		assertThat(names(json)).containsExactly("Bread");
		assertThat(read(json, "$.items[0].source")).isEqualTo("MANUAL");
		assertThat(read(json, "$.items[0].productId")).isNull();

		String id = read(json, "$.items[0].id");
		json = check("items/" + id, true);
		assertThat(read(json, "$.items[0].checked")).isEqualTo("true");
		assertThat(read(json, "$.inBasket")).isEqualTo("1");
	}

	@Test
	void anAddedNameThatMatchesAProductIsLinkedToIt() {
		String eggs = countedAt("Eggs", fridge, "piece", "4", "6");

		String json = ok(post(SHOPPING + "/items", "{\"name\":\"eggs\"}"), 201);
		assertThat(read(json, "$.items[0].name")).isEqualTo("Eggs");
		assertThat(read(json, "$.items[0].productId")).isEqualTo(eggs);
		assertThat(read(json, "$.items[0].places")).isEqualTo("Fridge");

		// later it runs low too: still listed once, as yours
		jdbc.sql("update stock_item set quantity = 1").update();
		assertThat(names(list())).containsExactly("Eggs");
	}

	@Test
	void addingSomethingAlreadyOnTheListIs409() {
		statusAt("Olive oil", pantry, "LOW");
		post(SHOPPING + "/items", "{\"name\":\"Bread\"}").expectStatus().isCreated();

		post(SHOPPING + "/items", "{\"name\":\"BREAD\"}")
				.expectStatus().isEqualTo(409)
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		post(SHOPPING + "/items", "{\"name\":\"olive oil\"}").expectStatus().isEqualTo(409);
		post(SHOPPING + "/items", "{\"name\":\"  \"}")
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("name");
		put(SHOPPING + "/items/01900000-0000-7000-8000-000000000000/checked", "{\"checked\":true}")
				.expectStatus().isNotFound();
	}

	// --- put away ---

	@Test
	void putAwayRestocksTickedProductsAndClearsTickedLines() {
		String oil = statusAt("Olive oil", pantry, "OUT");
		String cleaner = countedAt("Floor cleaner", sink, "piece", "1", "0");
		String eggs = countedAt("Eggs", fridge, "piece", "4", "2");
		String rice = countedAt("Rice", pantry, "kg", "0.5", "0.2");
		statusAt("Butter", fridge, "LOW"); // not ticked: stays
		check("products/" + oil, true);
		check("products/" + cleaner, true);
		check("products/" + eggs, true);
		check("products/" + rice, true);
		check("items/" + idOf(ok(post(SHOPPING + "/items", "{\"name\":\"Bread\"}"), 201), "Bread"), true);

		String json = ok(post(SHOPPING + "/put-away", "{}"), 200);
		assertThat(read(json, "$.putAway")).isEqualTo("5");
		assertThat((List<?>) JsonPath.read(json, "$.eventIds")).hasSize(4);
		assertThat(names(read(json, "$.list"))).containsExactly("Butter");

		assertThat(status(oil)).isEqualTo("HAVE");
		assertThat(quantity(cleaner)).isEqualByComparingTo("1"); // 0 → 1: one step reaches the minimum
		assertThat(quantity(eggs)).isEqualByComparingTo("4"); // 2 → 4: two steps
		assertThat(quantity(rice)).isEqualByComparingTo("0.7"); // 0.2 → 0.7: one 0.5 kg step
		assertThat(jdbc.sql("select count(*) from shopping_list_item").query(Integer.class).single()).isZero();
	}

	@Test
	void putAwayCanBeUndoneThroughTheStockEvents() {
		String oil = statusAt("Olive oil", pantry, "LOW");
		String cleaner = countedAt("Floor cleaner", sink, "piece", "1", "0");
		check("products/" + oil, true);
		check("products/" + cleaner, true);

		List<String> events = JsonPath.read(ok(post(SHOPPING + "/put-away", "{}"), 200), "$.eventIds");
		for (String eventId : events.reversed()) {
			client.post().uri("/api/v1/stock/events/" + eventId + "/undo").exchange().expectStatus().isOk();
		}

		assertThat(status(oil)).isEqualTo("LOW");
		assertThat(quantity(cleaner)).isEqualByComparingTo("0");
		assertThat(names(list())).containsExactly("Floor cleaner", "Olive oil");
	}

	@Test
	void anAddedProductWithNoStockIsPutAwayAtItsDefaultPlace() {
		String milk = createProduct("{\"name\":\"Milk\",\"trackingMode\":\"COUNTED\",\"defaultUnit\":\"l\",\"defaultLocationId\":\"%s\"}"
				.formatted(fridge));
		createProduct("{\"name\":\"Matches\",\"trackingMode\":\"COUNTED\"}"); // no place at all
		String milkLine = idOf(ok(post(SHOPPING + "/items", "{\"name\":\"Milk\"}"), 201), "Milk");
		String matchesLine = idOf(ok(post(SHOPPING + "/items", "{\"name\":\"Matches\"}"), 201), "Matches");
		check("items/" + milkLine, true);
		check("items/" + matchesLine, true);

		String json = ok(post(SHOPPING + "/put-away", "{}"), 200);
		assertThat(quantity(milk)).isEqualByComparingTo("0.5"); // one l step at the Fridge
		List<String> skipped = JsonPath.read(json, "$.skipped");
		assertThat(skipped).containsExactly("Matches");
	}

	// --- helpers ---

	private String statusAt(String name, String location, String status) {
		String id = createProduct("{\"name\":\"%s\",\"trackingMode\":\"STATUS\",\"defaultLocationId\":\"%s\"}"
				.formatted(name, location));
		post("/api/v1/stock", "{\"productId\":\"%s\",\"status\":\"%s\"}".formatted(id, status)).expectStatus().isOk();
		return id;
	}

	private void cycleTo(String productId, String location, String status) {
		post("/api/v1/stock", "{\"productId\":\"%s\",\"locationId\":\"%s\",\"status\":\"%s\"}"
				.formatted(productId, location, status)).expectStatus().isOk();
	}

	private String countedAt(String name, String location, String unit, String min, String quantity) {
		String id = createProduct("{\"name\":\"%s\",\"trackingMode\":\"COUNTED\",\"defaultUnit\":\"%s\",\"defaultLocationId\":\"%s\"%s}"
				.formatted(name, unit, location, min == null ? "" : ",\"minQuantity\":" + min));
		post("/api/v1/stock", "{\"productId\":\"%s\",\"quantity\":%s}".formatted(id, quantity)).expectStatus().isOk();
		return id;
	}

	private String list() {
		return ok(client.get().uri(SHOPPING).exchange(), 200);
	}

	private String check(String path, boolean checked) {
		return ok(put(SHOPPING + "/" + path + "/checked", "{\"checked\":" + checked + "}"), 200);
	}

	private String status(String productId) {
		return jdbc.sql("select status from stock_item where product_id = ?::uuid").param(productId)
				.query(String.class).single();
	}

	private BigDecimal quantity(String productId) {
		return jdbc.sql("select sum(quantity) from stock_item where product_id = ?::uuid").param(productId)
				.query(BigDecimal.class).single();
	}

	private static String idOf(String json, String name) {
		List<String> ids = JsonPath.read(json, "$.items[?(@.name=='" + name + "')].id");
		return ids.get(0);
	}

	private static List<String> names(String json) {
		return JsonPath.read(json, "$.items[*].name");
	}

	private static String ok(RestTestClient.ResponseSpec response, int status) {
		return response.expectStatus().isEqualTo(status).expectBody(String.class).returnResult().getResponseBody();
	}

	private static String read(String json, String path) {
		Object value = JsonPath.read(json, path);
		if (value == null) {
			return null;
		}
		if (value instanceof java.util.Map<?, ?> || value instanceof List<?>) {
			return net.minidev.json.JSONValue.toJSONString(value);
		}
		return value.toString();
	}

}
