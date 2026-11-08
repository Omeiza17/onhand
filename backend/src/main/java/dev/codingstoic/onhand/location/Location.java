package dev.codingstoic.onhand.location;

import java.util.UUID;

/** Where things live: Pantry, Fridge, Freezer… Listed in {@code sortOrder}. */
public record Location(UUID id, String name, int sortOrder) {
}
