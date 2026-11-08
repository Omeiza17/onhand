package dev.codingstoic.onhand.stock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One entry of the append-only stock log. */
record StockEvent(
		UUID id,
		UUID productId,
		UUID locationId,
		EventType type,
		BigDecimal quantityDelta,
		StockStatus statusFrom,
		StockStatus statusTo,
		String note,
		Instant undoneAt) {

	static StockEvent quantityChange(EventType type, StockItem item, BigDecimal delta, String note) {
		return new StockEvent(null, item.productId(), item.locationId(), type, delta, null, null, note, null);
	}

	static StockEvent statusChange(EventType type, StockItem item, StockStatus from, StockStatus to, String note) {
		return new StockEvent(null, item.productId(), item.locationId(), type, null, from, to, note, null);
	}

}
