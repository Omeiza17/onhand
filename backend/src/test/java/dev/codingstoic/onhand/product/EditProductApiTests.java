package dev.codingstoic.onhand.product;

import dev.codingstoic.onhand.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/** Edit product's plain fields (PATCH) and Remove from Onhand (archive / unarchive). */
class EditProductApiTests extends ApiTestSupport {

	private static final String PRODUCTS = "/api/v1/products";

	private RestTestClient.ResponseSpec patch(String id, String json) {
		return client.patch().uri(PRODUCTS + "/" + id).contentType(MediaType.APPLICATION_JSON).body(json).exchange();
	}

	@Test
	void patchChangesOnlyTheFieldsGiven() {
		String id = createProduct("""
				{"name":"Deordorant","kind":"FOOD","category":"Toiletries","trackingMode":"COUNTED","defaultUnit":"g","minQuantity":100}
				""");

		patch(id, "{\"name\":\" Deodorant \",\"kind\":\"HOUSEHOLD\"}")
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.name").isEqualTo("Deodorant")
				.jsonPath("$.kind").isEqualTo("HOUSEHOLD")
				.jsonPath("$.category").isEqualTo("Toiletries")
				.jsonPath("$.minQuantity").isEqualTo(100.0)
				.jsonPath("$.defaultUnit").isEqualTo("g");

		patch(id, "{\"stepSize\":50,\"minQuantity\":200}")
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.stepSize").isEqualTo(50.0)
				.jsonPath("$.minQuantity").isEqualTo(200.0)
				.jsonPath("$.name").isEqualTo("Deodorant");
	}

	@Test
	void blankCategoryAndZeroMinimumClear() {
		String id = createProduct("""
				{"name":"Rice","category":"Grains","trackingMode":"COUNTED","defaultUnit":"kg","minQuantity":1}
				""");

		patch(id, "{\"category\":\"  \",\"minQuantity\":0}")
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.category").doesNotExist()
				.jsonPath("$.minQuantity").doesNotExist();
	}

	@Test
	void patchValidation() {
		String counted = createProduct("{\"name\":\"Rice\",\"trackingMode\":\"COUNTED\"}");
		String status = createProduct("{\"name\":\"Salt\",\"trackingMode\":\"STATUS\"}");

		patch(counted, "{\"name\":\"   \"}")
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("name");
		patch(counted, "{\"stepSize\":0}")
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("stepSize");
		patch(status, "{\"stepSize\":1}")
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("stepSize");
		patch(status, "{\"minQuantity\":1}")
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errors[0].field").isEqualTo("minQuantity");
		// unit, mode and location are not plain fields: ignored by PATCH
		patch(counted, "{\"defaultUnit\":\"kg\",\"trackingMode\":\"STATUS\"}")
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.defaultUnit").isEqualTo("piece")
				.jsonPath("$.trackingMode").isEqualTo("COUNTED");
	}

	@Test
	void renamingToAnExistingNameIs409() {
		createProduct("{\"name\":\"Milk\",\"trackingMode\":\"STATUS\"}");
		String oat = createProduct("{\"name\":\"Oat milk\",\"trackingMode\":\"STATUS\"}");

		patch(oat, "{\"name\":\"MILK\"}")
				.expectStatus().isEqualTo(409)
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void archivedNamesCanBeReusedButThenNotUnarchived() {
		String old = createProduct("{\"name\":\"Milk\",\"trackingMode\":\"STATUS\"}");
		client.post().uri(PRODUCTS + "/" + old + "/archive").exchange().expectStatus().isOk();

		// a new "milk" is fine while the old one is archived
		createProduct("{\"name\":\"milk\",\"trackingMode\":\"COUNTED\"}");

		client.post().uri(PRODUCTS + "/" + old + "/unarchive").exchange()
				.expectStatus().isEqualTo(409)
				.expectBody().jsonPath("$.detail").value(String.class, d -> assertThat(d).contains("Milk"));
	}

	@Test
	void unarchiveRestoresIntoListingAndSearch() {
		String id = createProduct("{\"name\":\"Shampoo\",\"kind\":\"HOUSEHOLD\",\"trackingMode\":\"STATUS\"}");
		client.post().uri(PRODUCTS + "/" + id + "/archive").exchange().expectStatus().isOk();
		client.post().uri(PRODUCTS + "/" + id + "/unarchive").exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.archived").isEqualTo(false);

		client.get().uri(PRODUCTS + "?q=sham").exchange()
				.expectBody().jsonPath("$[0].name").isEqualTo("Shampoo");
	}

}
