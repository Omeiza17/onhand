package dev.codingstoic.onhand.stock;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A stock row as stored. Exactly one of {@code quantity} (COUNTED products) and
 * {@code status} (STATUS products) is set.
 */
record StockItem(UUID id, UUID productId, UUID locationId, BigDecimal quantity, StockStatus status) {

	boolean isCounted() {
		return quantity != null;
	}

}
