package dev.codingstoic.onhand.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** 400 for a request that is well-formed but not allowed, e.g. "+" on a STATUS product. */
public class BadRequestException extends ErrorResponseException {

	public BadRequestException(String detail) {
		super(HttpStatus.BAD_REQUEST, ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail), null);
	}

}
