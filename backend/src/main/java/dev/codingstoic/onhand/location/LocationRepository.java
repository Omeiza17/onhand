package dev.codingstoic.onhand.location;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class LocationRepository {

	private final JdbcClient jdbc;

	LocationRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Location> findAll() {
		return jdbc.sql("select id, name, sort_order from location order by sort_order, lower(name)")
				.query(LocationRepository::map)
				.list();
	}

	public Optional<Location> findById(UUID id) {
		return jdbc.sql("select id, name, sort_order from location where id = :id")
				.param("id", id)
				.query(LocationRepository::map)
				.optional();
	}

	public boolean existsById(UUID id) {
		return jdbc.sql("select exists(select 1 from location where id = :id)")
				.param("id", id)
				.query(Boolean.class)
				.single();
	}

	/** Inserts at {@code sortOrder}, or after the last location when it is null. */
	public Location insert(String name, Integer sortOrder) {
		return jdbc.sql("""
				insert into location (name, sort_order)
				values (:name, coalesce(:sortOrder, (select coalesce(max(sort_order) + 1, 0) from location)))
				returning id, name, sort_order
				""")
				.param("name", name)
				.param("sortOrder", sortOrder)
				.query(LocationRepository::map)
				.single();
	}

	/** Updates name and, when given, sort order. Empty if the location does not exist. */
	public Optional<Location> update(UUID id, String name, Integer sortOrder) {
		return jdbc.sql("""
				update location
				set name = :name, sort_order = coalesce(:sortOrder, sort_order), updated_at = now()
				where id = :id
				returning id, name, sort_order
				""")
				.param("id", id)
				.param("name", name)
				.param("sortOrder", sortOrder)
				.query(LocationRepository::map)
				.optional();
	}

	public boolean delete(UUID id) {
		return jdbc.sql("delete from location where id = :id").param("id", id).update() > 0;
	}

	private static Location map(ResultSet rs, int rowNum) throws SQLException {
		return new Location(rs.getObject("id", UUID.class), rs.getString("name"), rs.getInt("sort_order"));
	}

}
