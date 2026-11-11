package ar.rindetuplata.report;

/** Why an instrument is not in the report of a month: returned apart, never dropped silently. */
public enum LeftOutReason {
	/** No rate was known on the first day of the month (a plazo fijo: none captured by the first business day). */
	NOT_CAPTURED_AT_START,
	/** A day of the month had no capture within the last 3 days. */
	GAP_IN_CAPTURES,
	/** A fund class with no quote within 4 days before the previous month end. */
	MISSING_BASE_QUOTE,
	/** A fund class with no quote within 4 days before the month end. */
	MISSING_END_QUOTE,
	/** A bank that published no non-client rate in the month. */
	NOT_OFFERED_TO_NON_CLIENTS,
	/** Every capture of the instrument in the month is flagged INVALID or has no usable rate. */
	INVALID_DATA,
	/** A fund class denominated in a foreign currency: peso inflation does not apply to it. */
	FOREIGN_CURRENCY,
	/** A class reserved for the tax-regularization regime (Ley 27.743), not open to the general public. */
	RESTRICTED_CLASS,
	/** The fund class an FCI-backed wallet maps to has no quotes around the month. */
	FUND_CLASS_NOT_FOUND
}
