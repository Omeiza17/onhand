package dev.codingstoic.onhand.shopping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.codingstoic.onhand.common.ConflictException;
import dev.codingstoic.onhand.common.NotFoundException;
import dev.codingstoic.onhand.product.Product;
import dev.codingstoic.onhand.product.ProductRepository;
import dev.codingstoic.onhand.product.TrackingMode;
import dev.codingstoic.onhand.shopping.ShoppingRepository.Row;
import dev.codingstoic.onhand.stock.AddStockRequest;
import dev.codingstoic.onhand.stock.StockChange;
import dev.codingstoic.onhand.stock.StockService;
import dev.codingstoic.onhand.stock.StockStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShoppingService {

	private final ShoppingRepository shopping;

	private final ProductRepository products;

	private final StockService stock;

	ShoppingService(ShoppingRepository shopping, ProductRepository products, StockService stock) {
		this.shopping = shopping;
		this.products = products;
		this.stock = stock;
	}

	/**
	 * Running low at home, then added by you, then in the basket. A product you added yourself
	 * isn't listed twice when it also runs low.
	 */
	@Transactional
	public ShoppingList list() {
		shopping.deleteStaleTicks();
		List<ShoppingItem> manual = shopping.manualItems();
		Set<UUID> addedByYou = manual.stream()
				.map(ShoppingItem::productId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		List<ShoppingItem> auto = shopping.autoItems().stream()
				.filter(i -> !addedByYou.contains(i.productId()))
				.toList();
		List<ShoppingItem> items = Stream.of(
				auto.stream().filter(i -> !i.checked()),
				manual.stream().filter(i -> !i.checked()),
				Stream.concat(auto.stream(), manual.stream()).filter(ShoppingItem::checked))
				.flatMap(s -> s)
				.toList();
		int toBuy = (int) items.stream().filter(i -> !i.checked()).count();
		return new ShoppingList(toBuy, items.size() - toBuy, items);
	}

	/** "Add something…". Linked to a tracked product when the name matches one exactly. */
	@Transactional
	public ShoppingList add(AddItemRequest request) {
		String name = request.name().strip();
		UUID productId = shopping.productIdByName(name).orElse(null);
		if (productId != null) {
			name = shopping.productName(productId).orElse(name);
			if (shopping.isLow(productId)) {
				throw new ConflictException(name + " is already on the list.");
			}
		}
		try {
			shopping.insertManual(name, productId);
		}
		catch (DuplicateKeyException ex) {
			throw new ConflictException(name + " is already on the list.");
		}
		return list();
	}

	@Transactional
	public ShoppingList checkItem(UUID id, boolean checked) {
		if (!shopping.setManualChecked(id, checked)) {
			throw new NotFoundException("Shopping list item", id);
		}
		return list();
	}

	/** Tick or untick a running-low product. */
	@Transactional
	public ShoppingList checkProduct(UUID productId, boolean checked) {
		if (!checked) {
			shopping.untickProduct(productId);
			return list();
		}
		String name = shopping.productName(productId).orElseThrow(() -> new NotFoundException("Product", productId));
		if (!shopping.isLow(productId)) {
			throw new ConflictException(name + " isn't running low.");
		}
		shopping.tickProduct(productId);
		return list();
	}

	/**
	 * "Put n items away": restocks every product behind a ticked line, then clears all ticked
	 * lines. STATUS products go back to HAVE; COUNTED products get their step size, repeated until
	 * they're no longer below their minimum. All or nothing; each restock is a normal, undoable
	 * stock event.
	 */
	@Transactional
	public PutAwayResult putAway() {
		shopping.deleteStaleTicks();
		List<UUID> eventIds = new ArrayList<>();
		List<String> skipped = new ArrayList<>();
		for (UUID productId : shopping.tickedProductIds()) {
			Product product = products.findById(productId).orElse(null);
			if (product == null || product.archived()) {
				continue;
			}
			List<StockChange> changes = restock(product);
			if (changes.isEmpty()) {
				skipped.add(product.name());
			}
			changes.stream().map(StockChange::eventId).filter(Objects::nonNull).forEach(eventIds::add);
		}
		int cleared = shopping.deleteTicked();
		return new PutAwayResult(cleared, eventIds, skipped, list());
	}

	private List<StockChange> restock(Product product) {
		List<Row> rows = shopping.stockRows(product.id());
		String note = "put away";
		if (product.trackingMode() == TrackingMode.STATUS) {
			List<Row> notHave = rows.stream().filter(r -> r.status() != StockStatus.HAVE).toList();
			if (rows.isEmpty() && product.defaultLocationId() != null) {
				return List.of(stock.add(new AddStockRequest(product.id(), product.defaultLocationId(), null,
						StockStatus.HAVE, note)));
			}
			return notHave.stream()
					.map(r -> stock.add(new AddStockRequest(product.id(), r.locationId(), null, StockStatus.HAVE, note)))
					.toList();
		}
		// COUNTED: at least one step, more until no longer below the minimum
		BigDecimal step = product.effectiveStepSize();
		BigDecimal total = rows.stream().map(Row::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal steps = BigDecimal.ONE;
		if (product.minQuantity() != null && total.compareTo(product.minQuantity()) < 0) {
			steps = product.minQuantity().subtract(total).divide(step, 0, RoundingMode.CEILING).max(BigDecimal.ONE);
		}
		UUID place = rows.stream()
				.filter(r -> r.locationId().equals(product.defaultLocationId()))
				.findFirst()
				.or(() -> rows.stream().findFirst())
				.map(Row::locationId)
				.orElse(product.defaultLocationId());
		if (place == null) {
			return List.of();
		}
		return List.of(stock.add(new AddStockRequest(product.id(), place, step.multiply(steps), null, note)));
	}

}
