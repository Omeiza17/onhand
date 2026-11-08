package dev.codingstoic.onhand.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import dev.codingstoic.onhand.common.ConflictException;
import dev.codingstoic.onhand.common.InvalidRequestException;
import dev.codingstoic.onhand.common.NotFoundException;
import dev.codingstoic.onhand.common.UniqueConstraints;
import dev.codingstoic.onhand.location.LocationRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

	private final ProductRepository products;

	private final LocationRepository locations;

	ProductService(ProductRepository products, LocationRepository locations) {
		this.products = products;
		this.locations = locations;
	}

	public List<Product> search(String query) {
		return products.search(query);
	}

	public Product get(UUID id) {
		return products.findById(id).orElseThrow(() -> new NotFoundException("Product", id));
	}

	@Transactional
	public Product create(ProductRequest request) {
		ProductRequest product = normalize(request, Kind.FOOD);
		try {
			return products.insert(product);
		}
		catch (DuplicateKeyException ex) {
			throw conflict(ex, product);
		}
	}

	/** Sets how far one tap on −/+ moves (null = unit default). COUNTED products only. */
	@Transactional
	public Product setStepSize(UUID id, StepSizeRequest request) {
		Product product = get(id);
		if (product.trackingMode() != TrackingMode.COUNTED) {
			throw new InvalidRequestException("stepSize", "only allowed for COUNTED products");
		}
		return products.updateStepSize(id, request.stepSize())
				.orElseThrow(() -> new NotFoundException("Product", id));
	}

	/**
	 * Edit product's plain fields (name, kind, category, minimum, step size); omitted fields stay.
	 * Not recorded as stock events, so not undoable.
	 */
	@Transactional
	public Product patch(UUID id, ProductPatch patch) {
		Product current = get(id);
		String name = current.name();
		if (patch.name() != null) {
			name = patch.name().strip();
			if (name.isEmpty()) {
				throw new InvalidRequestException("name", "must not be blank");
			}
		}
		boolean counted = current.trackingMode() == TrackingMode.COUNTED;
		BigDecimal minQuantity = current.minQuantity();
		if (patch.minQuantity() != null) {
			if (!counted && patch.minQuantity().signum() > 0) {
				throw new InvalidRequestException("minQuantity", "only allowed for COUNTED products");
			}
			minQuantity = patch.minQuantity().signum() == 0 ? null : patch.minQuantity();
		}
		BigDecimal stepSize = current.stepSize();
		if (patch.stepSize() != null) {
			if (!counted) {
				throw new InvalidRequestException("stepSize", "only allowed for COUNTED products");
			}
			stepSize = patch.stepSize();
		}
		String category = patch.category() == null ? current.category() : blankToNull(patch.category());
		Kind kind = patch.kind() != null ? patch.kind() : current.kind();
		try {
			return products.updateDetails(id, name, kind, category, minQuantity, stepSize)
					.orElseThrow(() -> new NotFoundException("Product", id));
		}
		catch (DuplicateKeyException ex) {
			throw new ConflictException("A product named '" + name + "' already exists.");
		}
	}

	/**
	 * "Remove from Onhand": hides the product (and its stock) from Home, search and autocomplete.
	 * Nothing is deleted — stock events reference the product — so it can be restored.
	 */
	@Transactional
	public Product archive(UUID id) {
		return products.setArchived(id, true).orElseThrow(() -> new NotFoundException("Product", id));
	}

	/** Undo of "Remove from Onhand". Refused if another product has taken the name meanwhile. */
	@Transactional
	public Product unarchive(UUID id) {
		Product product = get(id);
		try {
			return products.setArchived(id, false).orElseThrow(() -> new NotFoundException("Product", id));
		}
		catch (DuplicateKeyException ex) {
			throw new ConflictException("Another product named '" + product.name() + "' exists now.");
		}
	}

	/**
	 * Applies the rules bean validation can't: fills in {@code defaultKind} when kind is omitted,
	 * trims text, turns blank optionals into null, keeps unit/min quantity to COUNTED products
	 * (defaulting the unit to piece), and checks the default location exists.
	 */
	private ProductRequest normalize(ProductRequest r, Kind defaultKind) {
		Unit unit = r.defaultUnit();
		if (r.trackingMode() == TrackingMode.STATUS) {
			if (r.defaultUnit() != null) {
				throw new InvalidRequestException("defaultUnit", "only allowed for COUNTED products");
			}
			if (r.minQuantity() != null) {
				throw new InvalidRequestException("minQuantity", "only allowed for COUNTED products");
			}
		}
		else if (unit == null) {
			unit = Unit.PIECE;
		}
		if (r.defaultLocationId() != null && !locations.existsById(r.defaultLocationId())) {
			throw new InvalidRequestException("defaultLocationId", "location does not exist");
		}
		Kind kind = r.kind() != null ? r.kind() : defaultKind;
		return new ProductRequest(r.name().strip(), kind, blankToNull(r.category()), r.trackingMode(), unit,
				r.defaultLocationId(), r.minQuantity(), blankToNull(r.barcode()));
	}

	private static ConflictException conflict(DuplicateKeyException ex, ProductRequest product) {
		if (UniqueConstraints.violated(ex, "product_barcode_uq")) {
			return new ConflictException("Another product already has barcode '" + product.barcode() + "'.");
		}
		return new ConflictException("A product named '" + product.name() + "' already exists.");
	}

	private static String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s.strip();
	}

}
