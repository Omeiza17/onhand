package dev.codingstoic.onhand.stock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import dev.codingstoic.onhand.product.ProductSnapshot;

/**
 * One entry of the append-only stock log. For MOVED, {@code locationId} is where the stock
 * went and {@code fromLocationId} where it came from. {@code productBefore} is set when the
 * change also changed the product (move, mode or unit change), so undo can restore it.
 */
record StockEvent(
		UUID id,
		UUID productId,
		UUID locationId,
		EventType type,
		BigDecimal quantityDelta,
		StockStatus statusFrom,
		StockStatus statusTo,
		String note,
		Instant undoneAt,
		UUID fromLocationId,
		ProductSnapshot productBefore) {

	static StockEvent quantityChange(EventType type, StockItem item, BigDecimal delta, String note) {
		return new StockEvent(null, item.productId(), item.locationId(), type, delta, null, null, note, null, null,
				null);
	}

	static StockEvent statusChange(EventType type, StockItem item, StockStatus from, StockStatus to, String note) {
		return new StockEvent(null, item.productId(), item.locationId(), type, null, from, to, note, null, null, null);
	}

	/** The whole item moved from {@code from} to its current location. */
	static StockEvent moved(StockItem moved, UUID from, ProductSnapshot productBefore) {
		return new StockEvent(null, moved.productId(), moved.locationId(), EventType.MOVED, moved.quantity(), null,
				moved.status(), null, null, from, productBefore);
	}

	/** Tracking mode or unit changed; the item's state changed with it. */
	static StockEvent converted(EventType type, StockItem item, BigDecimal delta, StockStatus from, StockStatus to,
			ProductSnapshot productBefore) {
		return new StockEvent(null, item.productId(), item.locationId(), type, delta, from, to, null, null, null,
				productBefore);
	}

}
