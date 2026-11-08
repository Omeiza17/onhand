package dev.codingstoic.onhand.shopping;

import java.util.List;
import java.util.UUID;

/**
 * Result of "Put n items away": how many ticked lines were cleared, the stock events the
 * restocks wrote (undo them to put things back), products that couldn't be restocked because
 * they have no place yet, and the list as it is now.
 */
public record PutAwayResult(int putAway, List<UUID> eventIds, List<String> skipped, ShoppingList list) {
}
