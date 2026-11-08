package dev.codingstoic.onhand.product;

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
		ProductRequest product = normalize(request);
		try {
			return products.insert(product);
		}
		catch (DuplicateKeyException ex) {
			throw conflict(ex, product);
		}
	}

	@Transactional
	public Product update(UUID id, ProductRequest request) {
		ProductRequest product = normalize(request);
		try {
			return products.update(id, product).orElseThrow(() -> new NotFoundException("Product", id));
		}
		catch (DuplicateKeyException ex) {
			throw conflict(ex, product);
		}
	}

	@Transactional
	public void delete(UUID id) {
		if (!products.delete(id)) {
			throw new NotFoundException("Product", id);
		}
	}

	/**
	 * Applies the rules bean validation can't: trims text, turns blank optionals into null,
	 * keeps unit/min quantity to COUNTED products (defaulting the unit to piece), and checks
	 * the default location exists.
	 */
	private ProductRequest normalize(ProductRequest r) {
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
		return new ProductRequest(r.name().strip(), blankToNull(r.category()), r.trackingMode(), unit,
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
