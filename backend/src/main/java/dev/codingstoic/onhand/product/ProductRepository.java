package dev.codingstoic.onhand.product;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {

	private static final String COLUMNS =
			"id, name, kind, category, tracking_mode, default_unit, default_location_id, min_quantity, step_size, barcode, archived_at";

	private final JdbcClient jdbc;

	ProductRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Products that are not archived, by name — all of them, or only those whose name contains
	 * {@code query} (case-insensitive).
	 */
	public List<Product> search(String query) {
		if (query == null || query.isBlank()) {
			return jdbc.sql("select " + COLUMNS + " from product where archived_at is null order by lower(name)")
					.query(ProductRepository::map)
					.list();
		}
		return jdbc.sql("select " + COLUMNS + " from product where archived_at is null and name ilike :pattern escape '\\' order by lower(name)")
				.param("pattern", "%" + escapeLike(query.strip()) + "%")
				.query(ProductRepository::map)
				.list();
	}

	public Optional<Product> findById(UUID id) {
		return jdbc.sql("select " + COLUMNS + " from product where id = :id")
				.param("id", id)
				.query(ProductRepository::map)
				.optional();
	}

	public Product insert(ProductRequest p) {
		return jdbc.sql("""
				insert into product (name, kind, category, tracking_mode, default_unit, default_location_id, min_quantity, barcode)
				values (:name, :kind, :category, :trackingMode, :defaultUnit, :defaultLocationId, :minQuantity, :barcode)
				returning\s""" + COLUMNS)
				.params(params(p))
				.query(ProductRepository::map)
				.single();
	}

	/**
	 * Replaces all editable fields. Step size is not one of them; it is kept, except that a change
	 * of unit or tracking mode resets it to the unit default (the old step would not fit).
	 * Empty if the product does not exist.
	 */
	public Optional<Product> update(UUID id, ProductRequest p) {
		return jdbc.sql("""
				update product
				set name = :name, kind = :kind, category = :category, tracking_mode = :trackingMode,
				    default_unit = :defaultUnit, default_location_id = :defaultLocationId,
				    min_quantity = :minQuantity, barcode = :barcode,
				    step_size = case
				        when tracking_mode = :trackingMode and default_unit is not distinct from :defaultUnit
				        then step_size
				    end,
				    updated_at = now()
				where id = :id
				returning\s""" + COLUMNS)
				.params(params(p))
				.param("id", id)
				.query(ProductRepository::map)
				.optional();
	}

	/** Sets the step size (null = unit default). Empty if the product does not exist. */
	public Optional<Product> updateStepSize(UUID id, BigDecimal stepSize) {
		return jdbc.sql("update product set step_size = :stepSize, updated_at = now() where id = :id returning\s" + COLUMNS)
				.param("id", id)
				.param("stepSize", stepSize)
				.query(ProductRepository::map)
				.optional();
	}

	public boolean hasStock(UUID id) {
		return jdbc.sql("select exists(select 1 from stock_item where product_id = :id)")
				.param("id", id)
				.query(Boolean.class)
				.single();
	}

	/** Edit product's plain fields. Empty if the product does not exist. */
	public Optional<Product> updateDetails(UUID id, String name, Kind kind, String category, BigDecimal minQuantity,
			BigDecimal stepSize) {
		return jdbc.sql("""
				update product
				set name = :name, kind = :kind, category = :category, min_quantity = :minQuantity,
				    step_size = :stepSize, updated_at = now()
				where id = :id
				returning\s""" + COLUMNS)
				.param("id", id)
				.param("name", name)
				.param("kind", kind.name())
				.param("category", category)
				.param("minQuantity", minQuantity)
				.param("stepSize", stepSize)
				.query(ProductRepository::map)
				.optional();
	}

	/** Sets the tracking fields together (they are checked together by the database). */
	public Product updateTracking(UUID id, ProductSnapshot t) {
		return jdbc.sql("""
				update product
				set tracking_mode = :mode, default_unit = :unit, min_quantity = :minQuantity,
				    step_size = :stepSize, default_location_id = :defaultLocationId, updated_at = now()
				where id = :id
				returning\s""" + COLUMNS)
				.param("id", id)
				.param("mode", t.trackingMode().name())
				.param("unit", t.defaultUnit() == null ? null : t.defaultUnit().code())
				.param("minQuantity", t.minQuantity())
				.param("stepSize", t.stepSize())
				.param("defaultLocationId", t.defaultLocationId())
				.query(ProductRepository::map)
				.single();
	}

	/** Archives (true) or restores (false). Empty if the product does not exist. */
	public Optional<Product> setArchived(UUID id, boolean archived) {
		return jdbc.sql("""
				update product
				set archived_at = case when :archived then coalesce(archived_at, now()) end, updated_at = now()
				where id = :id
				returning\s""" + COLUMNS)
				.param("id", id)
				.param("archived", archived)
				.query(ProductRepository::map)
				.optional();
	}

	/** Places where the product is at home right now. */
	public int stockPlaces(UUID id) {
		return jdbc.sql("select count(*) from stock_item where product_id = :id")
				.param("id", id)
				.query(Integer.class)
				.single();
	}

	private static Map<String, Object> params(ProductRequest p) {
		Map<String, Object> params = new HashMap<>();
		params.put("name", p.name());
		params.put("kind", p.kind().name());
		params.put("category", p.category());
		params.put("trackingMode", p.trackingMode().name());
		params.put("defaultUnit", p.defaultUnit() == null ? null : p.defaultUnit().code());
		params.put("defaultLocationId", p.defaultLocationId());
		params.put("minQuantity", p.minQuantity());
		params.put("barcode", p.barcode());
		return params;
	}

	private static String escapeLike(String s) {
		return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static Product map(ResultSet rs, int rowNum) throws SQLException {
		String unitCode = rs.getString("default_unit");
		Unit unit = unitCode == null ? null : Unit.fromCode(unitCode);
		BigDecimal stepSize = rs.getBigDecimal("step_size");
		return new Product(
				rs.getObject("id", UUID.class),
				rs.getString("name"),
				Kind.valueOf(rs.getString("kind")),
				rs.getString("category"),
				TrackingMode.valueOf(rs.getString("tracking_mode")),
				unit,
				rs.getObject("default_location_id", UUID.class),
				rs.getBigDecimal("min_quantity"),
				stepSize,
				Product.effectiveStep(unit, stepSize),
				rs.getString("barcode"),
				rs.getObject("archived_at", OffsetDateTime.class) != null);
	}

}
