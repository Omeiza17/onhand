package dev.codingstoic.onhand.location;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body for creating or updating a location. {@code sortOrder} is optional on create
 * (the location goes to the end) and on update (the position is kept).
 */
public record LocationRequest(
		@NotBlank @Size(max = 60) String name,
		@PositiveOrZero Integer sortOrder) {
}
