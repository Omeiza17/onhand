package dev.codingstoic.onhand.product;

import java.math.BigDecimal;
import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Units for COUNTED products, written in lowercase in JSON and in the database.
 * Measures are metric, as in UN/ECE Recommendation 20; counted containers are named after
 * UN/ECE Recommendation 21 package types. {@code uneceCode} records the matching code.
 * Each unit has a default step size: how far one tap on −/+ moves when the product sets none.
 */
public enum Unit {

	// measured (Rec 20)
	G("g", "GRM", "100"),
	KG("kg", "KGM", "0.5"),
	ML("ml", "MLT", "250"),
	L("l", "LTR", "0.5"),

	// counted (Rec 20 piece, Rec 21 package types)
	PIECE("piece", "H87", "1"),
	PACK("pack", "PK", "1"),
	TIN("tin", "TN", "1"),
	BOTTLE("bottle", "BO", "1"),
	JAR("jar", "JR", "1"),
	ROLL("roll", "RO", "1"),
	BOX("box", "BX", "1"),
	BAG("bag", "BG", "1"),
	CARTON("carton", "CT", "1"),
	TUBE("tube", "TU", "1"),
	TUB("tub", "TB", "1"),
	BAR("bar", "BR", "1"),
	SACHET("sachet", "SH", "1"),
	BUNCH("bunch", "BH", "1");

	private final String code;

	private final String uneceCode;

	private final BigDecimal defaultStep;

	Unit(String code, String uneceCode, String defaultStep) {
		this.code = code;
		this.uneceCode = uneceCode;
		this.defaultStep = new BigDecimal(defaultStep);
	}

	@JsonValue
	public String code() {
		return code;
	}

	/** UN/ECE Rec 20 unit code or Rec 21 package type code, for reference. */
	public String uneceCode() {
		return uneceCode;
	}

	public BigDecimal defaultStep() {
		return defaultStep;
	}

	public static Unit fromCode(String code) {
		return Arrays.stream(values())
				.filter(u -> u.code.equals(code))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown unit: " + code));
	}

}
