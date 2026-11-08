package dev.codingstoic.onhand.product;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonValue;

/** Metric units for COUNTED products. Written in lowercase in JSON and in the database. */
public enum Unit {

	PIECE("piece"), G("g"), KG("kg"), ML("ml"), L("l"), PACK("pack"), TIN("tin");

	private final String code;

	Unit(String code) {
		this.code = code;
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
