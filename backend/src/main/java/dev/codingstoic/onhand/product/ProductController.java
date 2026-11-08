package dev.codingstoic.onhand.product;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
class ProductController {

	private final ProductService service;

	ProductController(ProductService service) {
		this.service = service;
	}

	/** All products by name; {@code q} filters by name (case-insensitive, contains). */
	@GetMapping
	List<Product> list(@RequestParam(required = false) String q) {
		return service.search(q);
	}

	@GetMapping("/{id}")
	Product get(@PathVariable UUID id) {
		return service.get(id);
	}

	@PostMapping
	ResponseEntity<Product> create(@Valid @RequestBody ProductRequest request) {
		Product created = service.create(request);
		return ResponseEntity.created(URI.create("/api/v1/products/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	Product update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
		return service.update(id, request);
	}

	/** Set from the Home update panel; {@code {"stepSize": null}} resets to the unit default. */
	@PutMapping("/{id}/step-size")
	Product setStepSize(@PathVariable UUID id, @Valid @RequestBody StepSizeRequest request) {
		return service.setStepSize(id, request);
	}

	/** Edit product: name, kind, category, minimum, step size — omitted fields stay as they are. */
	@PatchMapping("/{id}")
	Product patch(@PathVariable UUID id, @Valid @RequestBody ProductPatch patch) {
		return service.patch(id, patch);
	}

	/** "Remove from Onhand". Products are never hard-deleted. */
	@PostMapping("/{id}/archive")
	Product archive(@PathVariable UUID id) {
		return service.archive(id);
	}

	/** Undo of archive. */
	@PostMapping("/{id}/unarchive")
	Product unarchive(@PathVariable UUID id) {
		return service.unarchive(id);
	}

}
