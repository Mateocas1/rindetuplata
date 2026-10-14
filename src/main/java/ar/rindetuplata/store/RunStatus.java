package ar.rindetuplata.store;

/** What a source produced for a capture date. */
public enum RunStatus {
	/** Every source of the kind answered. */
	OK,
	/** The kind answered with fewer sources than expected, naming the failed one in {@code failure}. */
	DEGRADED,
	/** No source of the kind answered. */
	FAILED,
	/** The T9 archive of that date has no capture of this source. */
	MISSING_IN_ARCHIVE
}
