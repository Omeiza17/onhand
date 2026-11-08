package dev.codingstoic.onhand.common;

/** One failed input field, listed under {@code errors} in a 400 ProblemDetail. */
public record InvalidField(String field, String message) {
}
