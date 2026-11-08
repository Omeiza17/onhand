package dev.codingstoic.onhand.stock;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Optional body for + / −. Without a body (one tap) the amount is 1. */
public record AmountRequest(
		@Positive @Digits(integer = 7, fraction = 3) BigDecimal amount,
		@Size(max = 200) String note) {
}
