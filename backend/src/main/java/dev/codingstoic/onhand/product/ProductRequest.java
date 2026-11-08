package dev.codingstoic.onhand.product;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body for creating or replacing a product. Only {@code name} and {@code trackingMode} are
 * required; {@code defaultUnit} and {@code minQuantity} are for COUNTED products only.
 */
public record ProductRequest(
		@NotBlank @Size(max = 100) String name,
		@Size(max = 50) String category,
		@NotNull TrackingMode trackingMode,
		Unit defaultUnit,
		UUID defaultLocationId,
		@PositiveOrZero @Digits(integer = 7, fraction = 3) BigDecimal minQuantity,
		@Size(max = 64) String barcode) {
}
