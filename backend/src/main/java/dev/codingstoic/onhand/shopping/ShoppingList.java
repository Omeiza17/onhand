package dev.codingstoic.onhand.shopping;

import java.util.List;

/**
 * The whole list: running-low items, then yours, then what's in the basket.
 * {@code toBuy} counts what isn't ticked yet (the Shopping tab badge).
 */
public record ShoppingList(int toBuy, int inBasket, List<ShoppingItem> items) {
}
