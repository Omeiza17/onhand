package dev.codingstoic.onhand.stock;

import java.math.BigDecimal;

import dev.codingstoic.onhand.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Proves a stock change and its event are written in one transaction: when writing the event
 * fails, the stock change must roll back too.
 */
class StockTransactionTests extends ApiTestSupport {

	@MockitoSpyBean
	StockEventRepository events;

	@Test
	void failedEventWriteRollsBackTheStockChange() {
		String pantry = createLocation("Pantry");
		String rice = createProduct("{\"name\":\"Rice\",\"trackingMode\":\"COUNTED\",\"defaultLocationId\":\"%s\"}"
				.formatted(pantry));
		String item = field(post("/api/v1/stock", "{\"productId\":\"%s\",\"quantity\":2}".formatted(rice))
				.expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody(), "id");

		doThrow(new IllegalStateException("simulated failure writing the event")).when(events).insert(any());

		client.post().uri("/api/v1/stock/" + item + "/increase").exchange().expectStatus().is5xxServerError();
		// a new item must not be created either
		post("/api/v1/stock", "{\"productId\":\"%s\",\"locationId\":\"%s\"}"
				.formatted(createProduct("{\"name\":\"Salt\",\"trackingMode\":\"STATUS\"}"), pantry))
				.expectStatus().is5xxServerError();

		// a move rolls back too: the item stays where it was, the default place unchanged
		String bathroom = createLocation("Bathroom");
		post("/api/v1/stock/" + item + "/move", "{\"locationId\":\"%s\"}".formatted(bathroom))
				.expectStatus().is5xxServerError();
		assertThat(jdbc.sql("select location_id::text from stock_item where id = ?::uuid").param(item)
				.query(String.class).single()).isEqualTo(pantry);
		assertThat(jdbc.sql("select default_location_id::text from product where id = ?::uuid").param(rice)
				.query(String.class).single()).isEqualTo(pantry);

		assertThat(jdbc.sql("select quantity from stock_item where id = ?::uuid").param(item)
				.query(BigDecimal.class).single()).isEqualByComparingTo("2");
		assertThat(jdbc.sql("select count(*) from stock_item").query(Integer.class).single()).isEqualTo(1);
		assertThat(jdbc.sql("select count(*) from stock_event").query(Integer.class).single()).isEqualTo(1);
	}

}
