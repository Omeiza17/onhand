package dev.codingstoic.onhand.stock;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Optional body for + / −. Without an amount (one tap) it moves by the product's step size. */
public record AmountRequest(
		@Positive @Digits(integer = 7, fraction = 3) BigDecimal amount,
		@Size(max = 200) String note) {
}
