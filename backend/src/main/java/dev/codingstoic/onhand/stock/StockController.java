package dev.codingstoic.onhand.stock;

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

/**
 * What's at home, and the one-tap quick updates. Every change returns the item and the
 * {@code eventId} to pass to {@code POST /events/{eventId}/undo}.
 */
@RestController
@RequestMapping("/api/v1/stock")
class StockController {

	private final StockService service;

	StockController(StockService service) {
		this.service = service;
	}

	@GetMapping
	List<StockItemView> list() {
		return service.list();
	}

	@PostMapping
	StockChange add(@Valid @RequestBody AddStockRequest request) {
		return service.add(request);
	}

	@PostMapping("/{id}/increase")
	StockChange increase(@PathVariable UUID id, @Valid @RequestBody(required = false) AmountRequest request) {
		return service.increase(id, request);
	}

	@PostMapping("/{id}/decrease")
	StockChange decrease(@PathVariable UUID id, @Valid @RequestBody(required = false) AmountRequest request) {
		return service.decrease(id, request);
	}

	@PutMapping("/{id}/quantity")
	StockChange setQuantity(@PathVariable UUID id, @Valid @RequestBody QuantityRequest request) {
		return service.setQuantity(id, request);
	}

	@PostMapping("/{id}/cycle")
	StockChange cycle(@PathVariable UUID id) {
		return service.cycleStatus(id);
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> remove(@PathVariable UUID id) {
		service.remove(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/events/{eventId}/undo")
	StockChange undo(@PathVariable UUID eventId) {
		return service.undo(eventId);
	}

}
