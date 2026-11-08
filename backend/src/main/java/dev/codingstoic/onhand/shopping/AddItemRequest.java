package dev.codingstoic.onhand.shopping;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** "Add something…": free text, linked to a product when the name matches one. */
public record AddItemRequest(@NotBlank @Size(max = 100) String name) {
}
