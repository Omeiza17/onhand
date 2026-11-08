package dev.codingstoic.onhand.stock;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Put a product somewhere. {@code locationId} defaults to the product's default location.
 * COUNTED: adds {@code quantity} (default 1). STATUS: sets HAVE; {@code quantity} not allowed.
 */
public record AddStockRequest(
		@NotNull UUID productId,
		UUID locationId,
		@Positive @Digits(integer = 7, fraction = 3) BigDecimal quantity,
		@Size(max = 200) String note) {
}
