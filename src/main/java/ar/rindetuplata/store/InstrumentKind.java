package ar.rindetuplata.store;

/** The kinds of instrument the store keeps. Stored as text, one instrument row per (kind, source, natural key). */
public enum InstrumentKind {
	/** One product row of the BCRA transparency regime. */
	PLAZO_FIJO_OFFICIAL,
	/** A bank's plazo fijo rates as aggregated by ArgentinaDatos. */
	PLAZO_FIJO_BANK,
	/** A money-market fund class. */
	FUND_CLASS,
	/** A platform yield on one currency. */
	CRYPTO_YIELD,
	/** A remunerated wallet or account. */
	WALLET
}
