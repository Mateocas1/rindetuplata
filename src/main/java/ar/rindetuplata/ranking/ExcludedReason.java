package ar.rindetuplata.ranking;

/** Why an instrument is not ranked: it is returned apart, never silently dropped. */
public enum ExcludedReason {
	/** The row is flagged INVALID by the validation layer; it is kept for audit only. */
	INVALID_DATA,
	/** The bank does not publish a non-client rate. */
	NOT_OFFERED_TO_NON_CLIENTS,
	/** The bank publishes no client rate for a client query. */
	NO_CLIENT_RATE,
	/** A fund class with fewer than two valid quotes has no yield yet. */
	INSUFFICIENT_HISTORY,
	/** A fund class whose latest quote is more than 7 days older than the query date. */
	STALE,
	/** A fund class whose cuotaparte fell: it has no TNA-equivalent and must not be ranked. */
	NEGATIVE_RETURN,
	/** The fund class an FCI-backed wallet maps to is absent from the fund quotes (the source has no class id). */
	FUND_CLASS_NOT_FOUND,
	/** A fund class denominated in a foreign currency: its cuotaparte is not pesos, so peso inflation does not apply. */
	FOREIGN_CURRENCY,
	/** A class reserved for the tax-regularization regime (Ley 27.743), not open to the general public. */
	RESTRICTED_CLASS
}
