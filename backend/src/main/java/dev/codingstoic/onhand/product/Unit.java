package dev.codingstoic.onhand.product;

import java.math.BigDecimal;
import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Metric units for COUNTED products. Written in lowercase in JSON and in the database. Each has
 * a default step size: how far one tap on −/+ moves when the product sets none.
 */
public enum Unit {

	PIECE("piece", "1"), G("g", "100"), KG("kg", "0.5"), ML("ml", "250"), L("l", "0.5"), PACK("pack", "1"),
	TIN("tin", "1");

	private final String code;

	private final BigDecimal defaultStep;

	Unit(String code, String defaultStep) {
		this.code = code;
		this.defaultStep = new BigDecimal(defaultStep);
	}

	public BigDecimal defaultStep() {
		return defaultStep;
	}

	@JsonValue
	public String code() {
		return code;
	}

	public static Unit fromCode(String code) {
		return Arrays.stream(values())
				.filter(u -> u.code.equals(code))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown unit: " + code));
	}

}
