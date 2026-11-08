package dev.codingstoic.onhand.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** 409: the request clashes with existing data (e.g. a duplicate name). */
public class ConflictException extends ErrorResponseException {

	public ConflictException(String detail) {
		super(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail), null);
	}

}
