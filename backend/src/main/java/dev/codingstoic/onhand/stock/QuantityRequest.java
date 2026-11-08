package dev.codingstoic.onhand.stock;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Exact update of a COUNTED item, from the Home update panel. Give exactly one of:
 * {@code quantity} — "Now I have" (absolute, recorded as ADJUSTED), or
 * {@code used} — "I used" (a delta, recorded as USED; can't exceed what is there).
 */
public record QuantityRequest(
		@PositiveOrZero @Digits(integer = 7, fraction = 3) BigDecimal quantity,
		@Positive @Digits(integer = 7, fraction = 3) BigDecimal used,
		@Size(max = 200) String note) {
}
