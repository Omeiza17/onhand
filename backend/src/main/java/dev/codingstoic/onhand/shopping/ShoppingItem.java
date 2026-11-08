package dev.codingstoic.onhand.shopping;

import java.math.BigDecimal;
import java.util.UUID;

import dev.codingstoic.onhand.product.Unit;

/**
 * One line of the shopping list. {@code source} AUTO: a product running low at home (derived
 * from stock; {@code id} is null, tick it by {@code productId}). MANUAL: added by you
 * ({@code id} set; {@code productId} set when the name matches a tracked product).
 * {@code reason}: LOW / OUT (STATUS products) or BELOW_MIN (COUNTED, below {@code minQuantity}).
 * {@code places}: where the product lives, e.g. "Under the sink".
 */
public record ShoppingItem(
		UUID id,
		UUID productId,
		String name,
		Source source,
		Reason reason,
		BigDecimal minQuantity,
		Unit unit,
		String places,
		boolean checked) {

	public enum Source {
		AUTO, MANUAL
	}

	public enum Reason {
		LOW, OUT, BELOW_MIN
	}

}
