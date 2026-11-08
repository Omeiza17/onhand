package dev.codingstoic.onhand.stock;

/** Kind of change recorded in the stock log. MOVED is reserved; no endpoint writes it yet. */
public enum EventType {
	ADDED, USED, MOVED, ADJUSTED, STATUS_CHANGED
}
