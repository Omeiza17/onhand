package dev.codingstoic.onhand.common;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * 400 for rules that bean validation on a single field can't express (cross-field rules,
 * references to other records). Same shape as annotation-based validation errors.
 */
public class InvalidRequestException extends ErrorResponseException {

	public InvalidRequestException(String field, String message) {
		super(HttpStatus.BAD_REQUEST, body(field, message), null);
	}

	private static ProblemDetail body(String field, String message) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request content.");
		problem.setProperty("errors", List.of(new InvalidField(field, message)));
		return problem;
	}

}
