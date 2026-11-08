package dev.codingstoic.onhand.product;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A product's tracking fields at one moment — stored with a stock event (as JSON) when a move
 * or a mode/unit change also changes the product, so undo can put them back exactly.
 */
public record ProductSnapshot(
		TrackingMode trackingMode,
		Unit defaultUnit,
		BigDecimal minQuantity,
		BigDecimal stepSize,
		UUID defaultLocationId) {

	public static ProductSnapshot of(Product p) {
		return new ProductSnapshot(p.trackingMode(), p.defaultUnit(), p.minQuantity(), p.stepSize(),
				p.defaultLocationId());
	}

}
