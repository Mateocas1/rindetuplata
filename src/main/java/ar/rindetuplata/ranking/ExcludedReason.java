package ar.rindetuplata.ranking;

/** Why an instrument is not ranked: it is returned apart, never silently dropped (S15). */
public enum ExcludedReason {
	/** The row is flagged INVALID by the validation layer; it is kept for audit only. */
	INVALID_DATA,
	/** The bank does not publish a non-client rate (S19). */
	NOT_OFFERED_TO_NON_CLIENTS,
	/** The bank publishes no client rate for a client query. */
	NO_CLIENT_RATE,
	/** A fund class with fewer than two valid quotes has no yield yet. */
	INSUFFICIENT_HISTORY,
	/** A fund class whose latest quote is more than 7 days older than the query date. */
	STALE,
	/** A fund class whose cuotaparte fell: it has no TNA-equivalent and must not be ranked (T4 note). */
	NEGATIVE_RETURN
}
