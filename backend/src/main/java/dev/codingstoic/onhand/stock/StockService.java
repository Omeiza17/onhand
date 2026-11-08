package dev.codingstoic.onhand.stock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.codingstoic.onhand.common.BadRequestException;
import dev.codingstoic.onhand.common.ConflictException;
import dev.codingstoic.onhand.common.InvalidRequestException;
import dev.codingstoic.onhand.common.NotFoundException;
import dev.codingstoic.onhand.location.LocationRepository;
import dev.codingstoic.onhand.product.Kind;
import dev.codingstoic.onhand.product.Product;
import dev.codingstoic.onhand.product.ProductRepository;
import dev.codingstoic.onhand.product.TrackingMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every change updates the stock row and appends its event in the same transaction, so the
 * current state and the log can never disagree. Rows are locked while they change.
 */
@Service
public class StockService {

	private final StockItemRepository items;

	private final StockEventRepository events;

	private final ProductRepository products;

	private final LocationRepository locations;

	StockService(StockItemRepository items, StockEventRepository events, ProductRepository products,
			LocationRepository locations) {
		this.items = items;
		this.events = events;
		this.products = products;
		this.locations = locations;
	}

	/** What's at home; {@code kind} null means all kinds. */
	public List<StockItemView> list(Kind kind) {
		return items.findAllViews(kind);
	}

	/** Puts a product at a location, creating the stock row if needed. */
	@Transactional
	public StockChange add(AddStockRequest request) {
		Product product = products.findById(request.productId())
				.orElseThrow(() -> new InvalidRequestException("productId", "product does not exist"));
		UUID locationId = request.locationId() != null ? request.locationId() : product.defaultLocationId();
		if (locationId == null) {
			throw new InvalidRequestException("locationId", "required when the product has no default location");
		}
		if (!locations.existsById(locationId)) {
			throw new InvalidRequestException("locationId", "location does not exist");
		}
		Optional<StockItem> existing = items.lock(product.id(), locationId);

		if (product.trackingMode() == TrackingMode.COUNTED) {
			BigDecimal amount = request.quantity() != null ? request.quantity() : BigDecimal.ONE;
			StockItem item = existing
					.map(i -> items.updateQuantity(i.id(), i.quantity().add(amount)))
					.orElseGet(() -> items.insertCounted(product.id(), locationId, amount));
			return record(item, StockEvent.quantityChange(EventType.ADDED, item, amount, request.note()));
		}
		if (request.quantity() != null) {
			throw new InvalidRequestException("quantity", "not used for STATUS products");
		}
		StockStatus from = existing.map(StockItem::status).orElse(null);
		StockItem item = existing
				.map(i -> items.updateStatus(i.id(), StockStatus.HAVE))
				.orElseGet(() -> items.insertStatus(product.id(), locationId, StockStatus.HAVE));
		return record(item, StockEvent.statusChange(EventType.ADDED, item, from, StockStatus.HAVE, request.note()));
	}

	/** "+" on a COUNTED item; one tap moves by the product's step size. */
	@Transactional
	public StockChange increase(UUID id, AmountRequest request) {
		StockItem item = lockCounted(id);
		BigDecimal amount = amount(request, item);
		StockItem updated = items.updateQuantity(id, item.quantity().add(amount));
		return record(updated, StockEvent.quantityChange(EventType.ADDED, updated, amount, note(request)));
	}

	/**
	 * "−" on a COUNTED item; one tap moves by the product's step size. Uses what is left if the
	 * amount is larger; nothing left is a 409.
	 */
	@Transactional
	public StockChange decrease(UUID id, AmountRequest request) {
		StockItem item = lockCounted(id);
		if (item.quantity().signum() == 0) {
			throw new ConflictException("Nothing left to use.");
		}
		BigDecimal used = amount(request, item).min(item.quantity());
		StockItem updated = items.updateQuantity(id, item.quantity().subtract(used));
		return record(updated, StockEvent.quantityChange(EventType.USED, updated, used.negate(), note(request)));
	}

	/**
	 * Exact update from the Home panel: "Now I have" ({@code quantity}, ADJUSTED; no change, no
	 * event) or "I used" ({@code used}, USED; more than is there is refused, not clamped, since
	 * it was typed).
	 */
	@Transactional
	public StockChange setQuantity(UUID id, QuantityRequest request) {
		if (request.quantity() != null && request.used() != null) {
			throw new InvalidRequestException("used", "give either quantity or used, not both");
		}
		if (request.quantity() == null && request.used() == null) {
			throw new InvalidRequestException("quantity", "quantity or used is required");
		}
		StockItem item = lockCounted(id);
		if (request.used() != null) {
			if (request.used().compareTo(item.quantity()) > 0) {
				throw new InvalidRequestException("used",
						"more than the " + item.quantity().stripTrailingZeros().toPlainString() + " there is");
			}
			StockItem updated = items.updateQuantity(id, item.quantity().subtract(request.used()));
			return record(updated,
					StockEvent.quantityChange(EventType.USED, updated, request.used().negate(), request.note()));
		}
		BigDecimal delta = request.quantity().subtract(item.quantity());
		if (delta.signum() == 0) {
			return new StockChange(view(id), null);
		}
		StockItem updated = items.updateQuantity(id, request.quantity());
		return record(updated, StockEvent.quantityChange(EventType.ADJUSTED, updated, delta, request.note()));
	}

	/** One tap on a STATUS item: HAVE → LOW → OUT → HAVE. */
	@Transactional
	public StockChange cycleStatus(UUID id) {
		StockItem item = lock(id);
		if (item.isCounted()) {
			throw new BadRequestException("This product is COUNTED; use increase or decrease.");
		}
		StockStatus next = item.status().next();
		StockItem updated = items.updateStatus(id, next);
		return record(updated, StockEvent.statusChange(EventType.STATUS_CHANGED, updated, item.status(), next, null));
	}

	/**
	 * Removes a product from a location entirely, together with that item's history (like
	 * deleting a product does). Not undoable. Used-up items can be cleared this way, which also
	 * lets their location be deleted.
	 */
	@Transactional
	public void remove(UUID id) {
		StockItem item = lock(id);
		events.deleteForItem(item.productId(), item.locationId());
		items.delete(id);
	}

	/**
	 * Reverses an event and marks it undone. Only the newest not-yet-undone event of an item can
	 * be undone, so undos walk back through an item's history in order. Undoing the event that
	 * created the item removes the item again.
	 */
	@Transactional
	public StockChange undo(UUID eventId) {
		StockEvent event = events.lock(eventId).orElseThrow(() -> new NotFoundException("Stock event", eventId));
		if (event.undoneAt() != null) {
			throw new ConflictException("This change has already been undone.");
		}
		Optional<StockItem> locked = items.lock(event.productId(), event.locationId());
		if (!events.latestActiveId(event.productId(), event.locationId()).map(eventId::equals).orElse(false)) {
			throw new ConflictException("This item has changed since; only its latest change can be undone.");
		}
		StockItem item = locked.orElseThrow(() -> new ConflictException("This stock item no longer exists."));

		events.markUndone(eventId);
		if (!events.hasActiveEvents(item.productId(), item.locationId())) {
			// this event created the item
			items.delete(item.id());
			return new StockChange(null, eventId);
		}
		switch (event.type()) {
			case ADDED, USED, ADJUSTED -> {
				if (event.quantityDelta() != null) {
					items.updateQuantity(item.id(), item.quantity().subtract(event.quantityDelta()));
				}
				else {
					items.updateStatus(item.id(), event.statusFrom());
				}
			}
			case STATUS_CHANGED -> items.updateStatus(item.id(), event.statusFrom());
			case MOVED -> throw new ConflictException("Undoing a move is not supported.");
		}
		return new StockChange(view(item.id()), eventId);
	}

	private StockChange record(StockItem item, StockEvent event) {
		UUID eventId = events.insert(event);
		return new StockChange(view(item.id()), eventId);
	}

	private StockItem lock(UUID id) {
		return items.lock(id).orElseThrow(() -> new NotFoundException("Stock item", id));
	}

	private StockItem lockCounted(UUID id) {
		StockItem item = lock(id);
		if (!item.isCounted()) {
			throw new BadRequestException("This product is tracked by STATUS; use cycle.");
		}
		return item;
	}

	private StockItemView view(UUID id) {
		return items.findView(id).orElseThrow(() -> new NotFoundException("Stock item", id));
	}

	/** The amount asked for, or the product's step size for a plain tap. */
	private BigDecimal amount(AmountRequest request, StockItem item) {
		if (request != null && request.amount() != null) {
			return request.amount();
		}
		return products.findById(item.productId())
				.map(Product::effectiveStepSize)
				.orElse(BigDecimal.ONE);
	}

	private static String note(AmountRequest request) {
		return request == null ? null : request.note();
	}

}
