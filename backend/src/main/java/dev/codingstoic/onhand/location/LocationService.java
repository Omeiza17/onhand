package dev.codingstoic.onhand.location;

import java.util.List;
import java.util.UUID;

import dev.codingstoic.onhand.common.ConflictException;
import dev.codingstoic.onhand.common.NotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationService {

	private final LocationRepository locations;

	LocationService(LocationRepository locations) {
		this.locations = locations;
	}

	public List<Location> list() {
		return locations.findAll();
	}

	@Transactional
	public Location create(LocationRequest request) {
		String name = request.name().strip();
		try {
			return locations.insert(name, request.sortOrder());
		}
		catch (DuplicateKeyException ex) {
			throw duplicateName(name);
		}
	}

	@Transactional
	public Location update(UUID id, LocationRequest request) {
		String name = request.name().strip();
		try {
			return locations.update(id, name, request.sortOrder())
					.orElseThrow(() -> new NotFoundException("Location", id));
		}
		catch (DuplicateKeyException ex) {
			throw duplicateName(name);
		}
	}

	/** Products that used this location as their default keep existing, with no default. */
	@Transactional
	public void delete(UUID id) {
		if (!locations.delete(id)) {
			throw new NotFoundException("Location", id);
		}
	}

	private static ConflictException duplicateName(String name) {
		return new ConflictException("A location named '" + name + "' already exists.");
	}

}
