package dev.codingstoic.onhand.stock;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Set the exact amount of a COUNTED item (e.g. after a recount). */
public record QuantityRequest(
		@NotNull @PositiveOrZero @Digits(integer = 7, fraction = 3) BigDecimal quantity,
		@Size(max = 200) String note) {
}
