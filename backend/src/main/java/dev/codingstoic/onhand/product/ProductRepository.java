package dev.codingstoic.onhand.product;

import java.sql.ResultSet;
import java.sql.SQLException;
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
			"id, name, category, tracking_mode, default_unit, default_location_id, min_quantity, barcode";

	private final JdbcClient jdbc;

	ProductRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** All products by name, or only those whose name contains {@code query} (case-insensitive). */
	public List<Product> search(String query) {
		if (query == null || query.isBlank()) {
			return jdbc.sql("select " + COLUMNS + " from product order by lower(name)")
					.query(ProductRepository::map)
					.list();
		}
		return jdbc.sql("select " + COLUMNS + " from product where name ilike :pattern escape '\\' order by lower(name)")
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
				insert into product (name, category, tracking_mode, default_unit, default_location_id, min_quantity, barcode)
				values (:name, :category, :trackingMode, :defaultUnit, :defaultLocationId, :minQuantity, :barcode)
				returning\s""" + COLUMNS)
				.params(params(p))
				.query(ProductRepository::map)
				.single();
	}

	/** Replaces all editable fields. Empty if the product does not exist. */
	public Optional<Product> update(UUID id, ProductRequest p) {
		return jdbc.sql("""
				update product
				set name = :name, category = :category, tracking_mode = :trackingMode,
				    default_unit = :defaultUnit, default_location_id = :defaultLocationId,
				    min_quantity = :minQuantity, barcode = :barcode, updated_at = now()
				where id = :id
				returning\s""" + COLUMNS)
				.params(params(p))
				.param("id", id)
				.query(ProductRepository::map)
				.optional();
	}

	public boolean hasStock(UUID id) {
		return jdbc.sql("select exists(select 1 from stock_item where product_id = :id)")
				.param("id", id)
				.query(Boolean.class)
				.single();
	}

	public boolean delete(UUID id) {
		return jdbc.sql("delete from product where id = :id").param("id", id).update() > 0;
	}

	private static Map<String, Object> params(ProductRequest p) {
		Map<String, Object> params = new HashMap<>();
		params.put("name", p.name());
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
		String unit = rs.getString("default_unit");
		return new Product(
				rs.getObject("id", UUID.class),
				rs.getString("name"),
				rs.getString("category"),
				TrackingMode.valueOf(rs.getString("tracking_mode")),
				unit == null ? null : Unit.fromCode(unit),
				rs.getObject("default_location_id", UUID.class),
				rs.getBigDecimal("min_quantity"),
				rs.getString("barcode"));
	}

}
