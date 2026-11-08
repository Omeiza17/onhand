package dev.codingstoic.onhand.stock;

/** State of a STATUS-tracked product. A quick tap cycles HAVE → LOW → OUT → HAVE. */
public enum StockStatus {

	HAVE, LOW, OUT;

	public StockStatus next() {
		return switch (this) {
			case HAVE -> LOW;
			case LOW -> OUT;
			case OUT -> HAVE;
		};
	}

}
