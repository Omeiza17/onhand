package dev.codingstoic.onhand.location;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/locations")
class LocationController {

	private final LocationService service;

	LocationController(LocationService service) {
		this.service = service;
	}

	@GetMapping
	List<Location> list() {
		return service.list();
	}

	@PostMapping
	ResponseEntity<Location> create(@Valid @RequestBody LocationRequest request) {
		Location created = service.create(request);
		return ResponseEntity.created(URI.create("/api/v1/locations/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	Location update(@PathVariable UUID id, @Valid @RequestBody LocationRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

}
