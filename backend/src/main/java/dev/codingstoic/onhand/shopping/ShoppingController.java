package dev.codingstoic.onhand.shopping;

import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The shopping list. Every change returns the list as it is now. */
@RestController
@RequestMapping("/api/v1/shopping")
class ShoppingController {

	private final ShoppingService service;

	ShoppingController(ShoppingService service) {
		this.service = service;
	}

	@GetMapping
	ShoppingList list() {
		return service.list();
	}

	/** "Add something…" */
	@PostMapping("/items")
	@ResponseStatus(HttpStatus.CREATED)
	ShoppingList add(@Valid @RequestBody AddItemRequest request) {
		return service.add(request);
	}

	/** Tick / untick something you added. */
	@PutMapping("/items/{id}/checked")
	ShoppingList checkItem(@PathVariable UUID id, @Valid @RequestBody CheckRequest request) {
		return service.checkItem(id, request.checked());
	}

	/** Tick / untick a running-low product. */
	@PutMapping("/products/{productId}/checked")
	ShoppingList checkProduct(@PathVariable UUID productId, @Valid @RequestBody CheckRequest request) {
		return service.checkProduct(productId, request.checked());
	}

	/** "Put n items away": restock what's ticked and clear it from the list. */
	@PostMapping("/put-away")
	PutAwayResult putAway() {
		return service.putAway();
	}

}
