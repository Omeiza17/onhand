package dev.codingstoic.onhand.product;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

/** Sets how far one tap on −/+ moves. {@code null} resets it to the unit's default. */
public record StepSizeRequest(@Positive @Digits(integer = 7, fraction = 3) BigDecimal stepSize) {
}
