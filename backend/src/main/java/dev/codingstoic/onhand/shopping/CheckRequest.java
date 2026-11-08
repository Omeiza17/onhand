package dev.codingstoic.onhand.shopping;

import jakarta.validation.constraints.NotNull;

/** Tick ("in the basket") or untick a line. */
public record CheckRequest(@NotNull Boolean checked) {
}
