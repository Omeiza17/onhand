package dev.codingstoic.onhand.shopping;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.codingstoic.onhand.product.Unit;
import dev.codingstoic.onhand.shopping.ShoppingItem.Reason;
import dev.codingstoic.onhand.shopping.ShoppingItem.Source;
import dev.codingstoic.onhand.stock.StockStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ShoppingRepository {

	/**
	 * Products running low right now (spec: STATUS products that are LOW/OUT anywhere, COUNTED
	 * products whose total across places is below min_quantity). Archived products never are.
	 */
	private static final String LOW = """
			with low as (
			    select p.id as product_id,
			           case when p.tracking_mode = 'STATUS'
			                then case when bool_or(s.status = 'OUT') then 'OUT' else 'LOW' end
			                else 'BELOW_MIN' end as reason
			    from product p
			    join stock_item s on s.product_id = p.id
			    where p.archived_at is null
			    group by p.id
			    having (p.tracking_mode = 'STATUS' and bool_or(s.status in ('LOW', 'OUT')))
			        or (p.tracking_mode = 'COUNTED' and p.min_quantity is not null
			            and sum(s.quantity) < p.min_quantity)
			)
			""";

	private static final String PLACES = """
			(select string_agg(l.name, ' · ' order by l.sort_order)
			 from stock_item s join location l on l.id = s.location_id
			 where s.product_id = p.id) as places""";

	private final JdbcClient jdbc;

	ShoppingRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Running-low products, with their tick. */
	List<ShoppingItem> autoItems() {
		return jdbc.sql(LOW + """
				select null::uuid as id, p.id as product_id, p.name, 'AUTO' as source, low.reason,
				       p.min_quantity, p.default_unit, a.checked_at,
				""" + PLACES + """

				from low
				join product p on p.id = low.product_id
				left join shopping_list_item a on a.product_id = p.id and a.source = 'AUTO'
				order by lower(p.name)
				""")
				.query(ShoppingRepository::map)
				.list();
	}

	/** Things you added, oldest first. */
	List<ShoppingItem> manualItems() {
		return jdbc.sql("""
				select i.id, i.product_id, i.name, 'MANUAL' as source, null as reason,
				       p.min_quantity, p.default_unit, i.checked_at,
				""" + PLACES + """

				from shopping_list_item i
				left join product p on p.id = i.product_id
				where i.source = 'MANUAL'
				order by i.created_at
				""")
				.query(ShoppingRepository::map)
				.list();
	}

	/** Ticks on products that aren't running low any more (restocked meanwhile). */
	void deleteStaleTicks() {
		jdbc.sql(LOW + """
				delete from shopping_list_item i
				where i.source = 'AUTO' and not exists (select 1 from low where low.product_id = i.product_id)
				""")
				.update();
	}

	boolean isLow(UUID productId) {
		return jdbc.sql(LOW + "select exists(select 1 from low where product_id = :id)")
				.param("id", productId)
				.query(Boolean.class)
				.single();
	}

	/** A tracked (not archived) product with exactly this name, ignoring case. */
	Optional<UUID> productIdByName(String name) {
		return jdbc.sql("select id from product where archived_at is null and lower(name) = lower(:name)")
				.param("name", name)
				.query(UUID.class)
				.optional();
	}

	Optional<String> productName(UUID productId) {
		return jdbc.sql("select name from product where id = :id").param("id", productId).query(String.class).optional();
	}

	void insertManual(String name, UUID productId) {
		jdbc.sql("insert into shopping_list_item (source, name, product_id) values ('MANUAL', :name, :productId)")
				.param("name", name)
				.param("productId", productId)
				.update();
	}

	boolean setManualChecked(UUID id, boolean checked) {
		return jdbc.sql("""
				update shopping_list_item set checked_at = case when :checked then now() end
				where id = :id and source = 'MANUAL'
				""")
				.param("id", id)
				.param("checked", checked)
				.update() > 0;
	}

	void tickProduct(UUID productId) {
		jdbc.sql("""
				insert into shopping_list_item (source, product_id, checked_at) values ('AUTO', :p, now())
				on conflict (product_id) where source = 'AUTO' do nothing
				""")
				.param("p", productId)
				.update();
	}

	void untickProduct(UUID productId) {
		jdbc.sql("delete from shopping_list_item where source = 'AUTO' and product_id = :p").param("p", productId).update();
	}

	/** Products behind ticked lines (running-low ticks and ticked manual items linked to a product). */
	List<UUID> tickedProductIds() {
		return jdbc.sql("""
				select distinct product_id from shopping_list_item
				where checked_at is not null and product_id is not null
				""")
				.query(UUID.class)
				.list();
	}

	/** Clears every ticked line; returns how many. */
	int deleteTicked() {
		return jdbc.sql("delete from shopping_list_item where checked_at is not null").update();
	}

	/** A product's stock rows, in location order: id, location, quantity, status. */
	List<Row> stockRows(UUID productId) {
		return jdbc.sql("""
				select s.id, s.location_id, s.quantity, s.status
				from stock_item s join location l on l.id = s.location_id
				where s.product_id = :p
				order by l.sort_order, lower(l.name)
				""")
				.param("p", productId)
				.query((rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getObject("location_id", UUID.class),
						rs.getBigDecimal("quantity"),
						rs.getString("status") == null ? null : StockStatus.valueOf(rs.getString("status"))))
				.list();
	}

	record Row(UUID id, UUID locationId, BigDecimal quantity, StockStatus status) {
	}

	private static ShoppingItem map(ResultSet rs, int rowNum) throws SQLException {
		String unit = rs.getString("default_unit");
		String reason = rs.getString("reason");
		return new ShoppingItem(
				rs.getObject("id", UUID.class),
				rs.getObject("product_id", UUID.class),
				rs.getString("name"),
				Source.valueOf(rs.getString("source")),
				reason == null ? null : Reason.valueOf(reason),
				rs.getBigDecimal("min_quantity"),
				unit == null ? null : Unit.fromCode(unit),
				rs.getString("places"),
				rs.getObject("checked_at") != null);
	}

}
