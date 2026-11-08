package dev.codingstoic.onhand.product;

import java.util.List;

import dev.codingstoic.onhand.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

class ProductApiTests extends ApiTestSupport {

	private static final String PRODUCTS = "/api/v1/products";

	@Test
	void createCountedProductWithAllFields() {
		String pantry = createLocation("Pantry");

		post(PRODUCTS, """
				{"name":" Basmati rice ","category":"Grains","trackingMode":"COUNTED","defaultUnit":"kg",
				 "defaultLocationId":"%s","minQuantity":0.5,"barcode":"5012345678900"}
				""".formatted(pantry))
				.expectStatus().isCreated()
				.expectHeader().value("Location", h -> assertThat(h).startsWith(PRODUCTS + "/"))
				.expectBody()
				.jsonPath("$.id").value(String.class, id -> assertThat(id.charAt(14)).isEqualTo('7'))
				.jsonPath("$.name").isEqualTo("Basmati rice")
				.jsonPath("$.category").isEqualTo("Grains")
				.jsonPath("$.trackingMode").isEqualTo("COUNTED")
				.jsonPath("$.defaultUnit").isEqualTo("kg")
				.jsonPath("$.defaultLocationId").isEqualTo(pantry)
				.jsonPath("$.minQuantity").isEqualTo(0.5)
				.jsonPath("$.barcode").isEqualTo("5012345678900");
	}

	@Test
	void countedProductWithoutUnitDefaultsToPiece() {
		post(PRODUCTS, "{\"name\":\"Eggs\",\"trackingMode\":\"COUNTED\"}")
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.defaultUnit").isEqualTo("piece")
				.jsonPath("$.category").doesNotExist();
	}

	@Test
	void statusProductNeedsOnlyNameAndMode() {
		post(PRODUCTS, "{\"name\":\"Cumin\",\"trackingMode\":\"STATUS\",\"category\":\"  \"}")
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.trackingMode").isEqualTo("STATUS")
				.jsonPath("$.defaultUnit").doesNotExist()
				.jsonPath("$.minQuantity").doesNotExist()
				// blank optional text is stored as null
				.jsonPath("$.category").doesNotExist();
	}

	@Test
	void getUpdateAndDelete() {
		String id = createProduct("{\"name\":\"Oats\",\"trackingMode\":\"COUNTED\",\"defaultUnit\":\"g\"}");

		client.get().uri(PRODUCTS + "/" + id).exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.name").isEqualTo("Oats");

		put(PRODUCTS + "/" + id, "{\"name\":\"Rolled oats\",\"trackingMode\":\"STATUS\"}")
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.name").isEqualTo("Rolled oats")
				.jsonPath("$.trackingMode").isEqualTo("STATUS")
				.jsonPath("$.defaultUnit").doesNotExist();

		client.delete().uri(PRODUCTS + "/" + id).exchange().expectStatus().isNoContent();
		client.get().uri(PRODUCTS + "/" + id).exchange()
				.expectStatus().isNotFound()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void listIsSortedByNameAndFiltersByQuery() {
		createProduct("{\"name\":\"eggs\",\"trackingMode\":\"COUNTED\"}");
		createProduct("{\"name\":\"Basmati rice\",\"trackingMode\":\"COUNTED\"}");
		createProduct("{\"name\":\"Brown rice\",\"trackingMode\":\"COUNTED\"}");
		createProduct("{\"name\":\"100% juice\",\"trackingMode\":\"COUNTED\"}");

		client.get().uri(PRODUCTS).exchange()
				.expectBody()
				.jsonPath("$[*].name").isEqualTo(List.of("100% juice", "Basmati rice", "Brown rice", "eggs"));

		client.get().uri(PRODUCTS + "?q=RICE").exchange()
				.expectBody()
				.jsonPath("$[*].name").isEqualTo(List.of("Basmati rice", "Brown rice"));

		// % is matched literally, not as a wildcard
		client.get().uri(PRODUCTS + "?q={q}", "%").exchange()
				.expectBody()
				.jsonPath("$[*].name").isEqualTo(List.of("100% juice"));
	}

	@Test
	void missingRequiredFieldsIs400WithEachField() {
		post(PRODUCTS, "{\"name\":\"\"}")
				.expectStatus().isBadRequest()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.errors[*].field").isEqualTo(List.of("name", "trackingMode"));
	}

	@Test
	void unknownEnumValueIs400() {
		post(PRODUCTS, "{\"name\":\"Milk\",\"trackingMode\":\"COUNTED\",\"defaultUnit\":\"gallon\"}")
				.expectStatus().isBadRequest()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		post(PRODUCTS, "{\"name\":\"Milk\",\"trackingMode\":\"SOMETIMES\"}")
				.expectStatus().isBadRequest();
	}

	@Test
	void negativeOrTooPreciseMinQuantityIs400() {
		post(PRODUCTS, "{\"name\":\"Milk\",\"trackingMode\":\"COUNTED\",\"minQuantity\":-1}")
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("minQuantity");
		post(PRODUCTS, "{\"name\":\"Milk\",\"trackingMode\":\"COUNTED\",\"minQuantity\":0.0001}")
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("minQuantity");
	}

	@Test
	void unitOrMinQuantityOnStatusProductIs400() {
		post(PRODUCTS, "{\"name\":\"Salt\",\"trackingMode\":\"STATUS\",\"defaultUnit\":\"g\"}")
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("defaultUnit");
		post(PRODUCTS, "{\"name\":\"Salt\",\"trackingMode\":\"STATUS\",\"minQuantity\":1}")
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("minQuantity");
	}

	@Test
	void unknownDefaultLocationIs400() {
		post(PRODUCTS, "{\"name\":\"Milk\",\"trackingMode\":\"COUNTED\",\"defaultLocationId\":\"01900000-0000-7000-8000-000000000000\"}")
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("defaultLocationId");
	}

	@Test
	void duplicateNameIgnoringCaseIs409() {
		createProduct("{\"name\":\"Eggs\",\"trackingMode\":\"COUNTED\"}");

		post(PRODUCTS, "{\"name\":\"eggs\",\"trackingMode\":\"COUNTED\"}")
				.expectStatus().isEqualTo(409)
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody().jsonPath("$.detail").value(String.class, d -> assertThat(d).contains("named"));
	}

	@Test
	void duplicateBarcodeIs409ButMissingBarcodesDoNotClash() {
		createProduct("{\"name\":\"Beans\",\"trackingMode\":\"COUNTED\",\"barcode\":\"123\"}");
		createProduct("{\"name\":\"Lentils\",\"trackingMode\":\"COUNTED\"}");
		createProduct("{\"name\":\"Chickpeas\",\"trackingMode\":\"COUNTED\"}");

		post(PRODUCTS, "{\"name\":\"Other beans\",\"trackingMode\":\"COUNTED\",\"barcode\":\"123\"}")
				.expectStatus().isEqualTo(409)
				.expectBody().jsonPath("$.detail").value(String.class, d -> assertThat(d).contains("barcode"));
	}

	@Test
	void unknownIdIs404() {
		String missing = PRODUCTS + "/01900000-0000-7000-8000-000000000000";
		client.get().uri(missing).exchange().expectStatus().isNotFound();
		put(missing, "{\"name\":\"X\",\"trackingMode\":\"STATUS\"}").expectStatus().isNotFound();
		client.delete().uri(missing).exchange().expectStatus().isNotFound();
	}

}
