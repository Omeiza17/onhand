package dev.codingstoic.onhand.stock;

import java.util.UUID;

/**
 * Result of a stock change: the item as it is now, and the event that was written —
 * pass {@code eventId} to the undo endpoint. After an undo that removed the item,
 * {@code item} is null. {@code eventId} is null when nothing changed.
 */
public record StockChange(StockItemView item, UUID eventId) {
}
