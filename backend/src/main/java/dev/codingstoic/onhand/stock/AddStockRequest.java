package dev.codingstoic.onhand.stock;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Put a product somewhere. {@code locationId} defaults to the product's default location.
 * COUNTED: adds {@code quantity} (default 1; 0 puts the item at home with nothing left, e.g.
 * so it goes on the shopping list). STATUS: sets {@code status} (default HAVE).
 */
public record AddStockRequest(
		@NotNull UUID productId,
		UUID locationId,
		@PositiveOrZero @Digits(integer = 7, fraction = 3) BigDecimal quantity,
		StockStatus status,
		@Size(max = 200) String note) {
}
