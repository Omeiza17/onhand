package dev.codingstoic.onhand.product;

import java.math.BigDecimal;
import java.util.UUID;

/** A kind of thing kept at home, e.g. "Basmati rice", "Eggs", "Cumin". */
public record Product(
		UUID id,
		String name,
		Kind kind,
		String category,
		TrackingMode trackingMode,
		Unit defaultUnit,
		UUID defaultLocationId,
		BigDecimal minQuantity,
		String barcode) {
}
