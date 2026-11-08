package dev.codingstoic.onhand.stock;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.codingstoic.onhand.product.TrackingMode;
import dev.codingstoic.onhand.product.Unit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class StockItemRepository {

	private static final String VIEW = """
			select s.id, s.product_id, p.name as product_name, p.category, p.tracking_mode, p.default_unit,
			       s.location_id, l.name as location_name, l.sort_order as location_sort_order,
			       s.quantity, s.status, s.expires_on, s.updated_at
			from stock_item s
			join product p on p.id = s.product_id
			join location l on l.id = s.location_id
			""";

	private static final String RETURNING = " returning id, product_id, location_id, quantity, status";

	private final JdbcClient jdbc;

	StockItemRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Everything at home, ordered for display: by location order, then product name. */
	List<StockItemView> findAllViews() {
		return jdbc.sql(VIEW + " order by l.sort_order, lower(l.name), lower(p.name)")
				.query(StockItemRepository::mapView)
				.list();
	}

	Optional<StockItemView> findView(UUID id) {
		return jdbc.sql(VIEW + " where s.id = :id").param("id", id).query(StockItemRepository::mapView).optional();
	}

	/** Reads and row-locks an item so concurrent changes to it run one after another. */
	Optional<StockItem> lock(UUID id) {
		return jdbc.sql("select id, product_id, location_id, quantity, status from stock_item where id = :id for update")
				.param("id", id)
				.query(StockItemRepository::map)
				.optional();
	}

	Optional<StockItem> lock(UUID productId, UUID locationId) {
		return jdbc.sql("""
				select id, product_id, location_id, quantity, status from stock_item
				where product_id = :productId and location_id = :locationId
				for update
				""")
				.param("productId", productId)
				.param("locationId", locationId)
				.query(StockItemRepository::map)
				.optional();
	}

	StockItem insertCounted(UUID productId, UUID locationId, BigDecimal quantity) {
		return jdbc.sql("insert into stock_item (product_id, location_id, quantity) values (:p, :l, :q)" + RETURNING)
				.param("p", productId).param("l", locationId).param("q", quantity)
				.query(StockItemRepository::map)
				.single();
	}

	StockItem insertStatus(UUID productId, UUID locationId, StockStatus status) {
		return jdbc.sql("insert into stock_item (product_id, location_id, status) values (:p, :l, :s)" + RETURNING)
				.param("p", productId).param("l", locationId).param("s", status.name())
				.query(StockItemRepository::map)
				.single();
	}

	StockItem updateQuantity(UUID id, BigDecimal quantity) {
		return jdbc.sql("update stock_item set quantity = :q, updated_at = now() where id = :id" + RETURNING)
				.param("id", id).param("q", quantity)
				.query(StockItemRepository::map)
				.single();
	}

	StockItem updateStatus(UUID id, StockStatus status) {
		return jdbc.sql("update stock_item set status = :s, updated_at = now() where id = :id" + RETURNING)
				.param("id", id).param("s", status.name())
				.query(StockItemRepository::map)
				.single();
	}

	void delete(UUID id) {
		jdbc.sql("delete from stock_item where id = :id").param("id", id).update();
	}

	private static StockItem map(ResultSet rs, int rowNum) throws SQLException {
		String status = rs.getString("status");
		return new StockItem(
				rs.getObject("id", UUID.class),
				rs.getObject("product_id", UUID.class),
				rs.getObject("location_id", UUID.class),
				rs.getBigDecimal("quantity"),
				status == null ? null : StockStatus.valueOf(status));
	}

	private static StockItemView mapView(ResultSet rs, int rowNum) throws SQLException {
		String unit = rs.getString("default_unit");
		String status = rs.getString("status");
		return new StockItemView(
				rs.getObject("id", UUID.class),
				rs.getObject("product_id", UUID.class),
				rs.getString("product_name"),
				rs.getString("category"),
				TrackingMode.valueOf(rs.getString("tracking_mode")),
				unit == null ? null : Unit.fromCode(unit),
				rs.getObject("location_id", UUID.class),
				rs.getString("location_name"),
				rs.getInt("location_sort_order"),
				rs.getBigDecimal("quantity"),
				status == null ? null : StockStatus.valueOf(status),
				rs.getObject("expires_on", LocalDate.class),
				rs.getObject("updated_at", OffsetDateTime.class).toInstant());
	}

}
