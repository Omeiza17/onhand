package dev.codingstoic.onhand.common;

import java.util.Comparator;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into RFC 9457 Problem Details. Builds on Spring's own handler, adding a
 * per-field {@code errors} list to validation failures and mapping unique-key clashes to 409.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<InvalidField> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> new InvalidField(e.getField(), e.getDefaultMessage()))
				.sorted(Comparator.comparing(InvalidField::field))
				.toList();
		ProblemDetail body = ex.getBody();
		body.setProperty("errors", errors);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	/** Safety net: a unique constraint the service didn't translate into a specific message. */
	@ExceptionHandler(DuplicateKeyException.class)
	ProblemDetail handleDuplicateKey(DuplicateKeyException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "A record with the same unique value already exists.");
	}

}
