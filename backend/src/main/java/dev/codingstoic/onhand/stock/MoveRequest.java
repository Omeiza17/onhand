package dev.codingstoic.onhand.stock;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Edit product: the item moves, whole, to another location. */
public record MoveRequest(@NotNull UUID locationId) {
}
