package dev.codingstoic.onhand.stock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import dev.codingstoic.onhand.product.Kind;
import dev.codingstoic.onhand.product.TrackingMode;
import dev.codingstoic.onhand.product.Unit;

/**
 * A stock row with what the Home screen needs to show and group it. {@code stepSize} is how far
 * one tap on −/+ moves (the product's step or its unit default); null for STATUS items.
 */
public record StockItemView(
		UUID id,
		UUID productId,
		String productName,
		Kind kind,
		String category,
		TrackingMode trackingMode,
		Unit unit,
		UUID locationId,
		String locationName,
		int locationSortOrder,
		BigDecimal quantity,
		BigDecimal stepSize,
		StockStatus status,
		LocalDate expiresOn,
		Instant updatedAt) {
}
