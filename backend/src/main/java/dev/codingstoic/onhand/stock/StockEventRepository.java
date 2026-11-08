package dev.codingstoic.onhand.stock;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.codingstoic.onhand.product.ProductSnapshot;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
class StockEventRepository {

	private static final String COLUMNS = """
			id, product_id, location_id, type, quantity_delta, status_from, status_to, note, undone_at,
			from_location_id, product_before::text as product_before""";

	private final JdbcClient jdbc;

	private final JsonMapper json;

	StockEventRepository(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	/** Appends an event and returns its id. */
	UUID insert(StockEvent e) {
		Map<String, Object> params = new HashMap<>();
		params.put("productId", e.productId());
		params.put("locationId", e.locationId());
		params.put("type", e.type().name());
		params.put("delta", e.quantityDelta());
		params.put("from", e.statusFrom() == null ? null : e.statusFrom().name());
		params.put("to", e.statusTo() == null ? null : e.statusTo().name());
		params.put("note", e.note());
		params.put("fromLocationId", e.fromLocationId());
		params.put("productBefore", e.productBefore() == null ? null : json.writeValueAsString(e.productBefore()));
		return jdbc.sql("""
				insert into stock_event (product_id, location_id, type, quantity_delta, status_from, status_to, note,
				                         from_location_id, product_before)
				values (:productId, :locationId, :type, :delta, :from, :to, :note,
				        :fromLocationId, cast(:productBefore as jsonb))
				returning id
				""")
				.params(params)
				.query(UUID.class)
				.single();
	}

	/** Reads and row-locks an event, so two undos of the same event can't both succeed. */
	Optional<StockEvent> lock(UUID id) {
		return jdbc.sql("select " + COLUMNS + " from stock_event where id = :id for update")
				.param("id", id)
				.query(this::map)
				.optional();
	}

	/**
	 * The newest event of a product at a location that has not been undone — including moves
	 * out of that location.
	 */
	Optional<UUID> latestActiveId(UUID productId, UUID locationId) {
		return jdbc.sql("""
				select id from stock_event
				where product_id = :p and (location_id = :l or from_location_id = :l) and undone_at is null
				order by occurred_at desc, id desc
				limit 1
				""")
				.param("p", productId).param("l", locationId)
				.query(UUID.class)
				.optional();
	}

	boolean hasActiveEvents(UUID productId, UUID locationId) {
		return jdbc.sql("""
				select exists(select 1 from stock_event
				              where product_id = :p and location_id = :l and undone_at is null)
				""")
				.param("p", productId).param("l", locationId)
				.query(Boolean.class)
				.single();
	}

	/**
	 * Deletes a product's history at locations where it has no stock row any more (after its
	 * item was removed) — that history can no longer be undone and would confuse a fresh start.
	 */
	void deleteOrphaned(UUID productId) {
		jdbc.sql("""
				delete from stock_event e
				where e.product_id = :p
				  and not exists (select 1 from stock_item s where s.product_id = :p and s.location_id = e.location_id)
				""")
				.param("p", productId)
				.update();
	}

	void markUndone(UUID id) {
		jdbc.sql("update stock_event set undone_at = now() where id = :id").param("id", id).update();
	}

	private StockEvent map(ResultSet rs, int rowNum) throws SQLException {
		String from = rs.getString("status_from");
		String to = rs.getString("status_to");
		OffsetDateTime undoneAt = rs.getObject("undone_at", OffsetDateTime.class);
		String productBefore = rs.getString("product_before");
		return new StockEvent(
				rs.getObject("id", UUID.class),
				rs.getObject("product_id", UUID.class),
				rs.getObject("location_id", UUID.class),
				EventType.valueOf(rs.getString("type")),
				rs.getBigDecimal("quantity_delta"),
				from == null ? null : StockStatus.valueOf(from),
				to == null ? null : StockStatus.valueOf(to),
				rs.getString("note"),
				undoneAt == null ? null : undoneAt.toInstant(),
				rs.getObject("from_location_id", UUID.class),
				productBefore == null ? null : json.readValue(productBefore, ProductSnapshot.class));
	}

}
