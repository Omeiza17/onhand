package dev.codingstoic.onhand.stock;

import java.math.BigDecimal;

import dev.codingstoic.onhand.product.TrackingMode;
import dev.codingstoic.onhand.product.Unit;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Edit product: change how a product is tracked, with what is at home made explicit.
 * COUNTED → STATUS: no other fields; the item becomes HAVE (some left) or OUT (none).
 * STATUS → COUNTED, or a new unit: {@code unit} (default piece from STATUS) and
 * {@code quantity}, the amount at home now — no automatic conversion between units.
 */
public record ConvertRequest(
		@NotNull TrackingMode trackingMode,
		Unit unit,
		@PositiveOrZero @Digits(integer = 7, fraction = 3) BigDecimal quantity) {
}
