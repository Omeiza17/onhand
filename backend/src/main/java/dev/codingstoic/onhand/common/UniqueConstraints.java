package dev.codingstoic.onhand.common;

import org.springframework.dao.DuplicateKeyException;

/** Identifies which unique index a {@link DuplicateKeyException} came from. */
public final class UniqueConstraints {

	private UniqueConstraints() {
	}

	/** Postgres names the violated index in the message: {@code ... unique constraint "product_name_uq"}. */
	public static boolean violated(DuplicateKeyException ex, String constraintName) {
		String message = ex.getMostSpecificCause().getMessage();
		return message != null && message.contains("\"" + constraintName + "\"");
	}

}
