package dev.codingstoic.onhand.product;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A kind of thing kept at home, e.g. "Basmati rice", "Eggs", "Cumin". For COUNTED products,
 * {@code stepSize} is the stored step (null = unit default) and {@code effectiveStepSize} is
 * what one tap on −/+ actually moves; both are null for STATUS products.
 */
public record Product(
		UUID id,
		String name,
		Kind kind,
		String category,
		TrackingMode trackingMode,
		Unit defaultUnit,
		UUID defaultLocationId,
		BigDecimal minQuantity,
		BigDecimal stepSize,
		BigDecimal effectiveStepSize,
		String barcode) {

	/** How far one tap moves for a product with this unit and stored step. */
	public static BigDecimal effectiveStep(Unit unit, BigDecimal stepSize) {
		if (stepSize != null) {
			return stepSize;
		}
		return unit == null ? null : unit.defaultStep();
	}

}
