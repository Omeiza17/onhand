package dev.codingstoic.onhand.product;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Edit product's plain fields. Omitted (or null) fields stay as they are. A blank
 * {@code category} clears it; {@code minQuantity} 0 switches the shopping-list threshold off.
 * Unit, tracking mode and location change through the stock convert/move endpoints instead,
 * because they change what is at home.
 */
public record ProductPatch(
		@Size(max = 100) String name,
		Kind kind,
		@Size(max = 50) String category,
		@PositiveOrZero @Digits(integer = 7, fraction = 3) BigDecimal minQuantity,
		@Positive @Digits(integer = 7, fraction = 3) BigDecimal stepSize) {
}
