package ar.rindetuplata.ranking;

/** Which stored rate of an instrument the simulation actually used. */
public enum RateKind {
	/** The bank's non-client TNA: the S19 default. */
	NON_CLIENT,
	/** The bank's client TNA. */
	CLIENT,
	/** A client term band that replaced the base client rate. */
	CLIENT_TERM_BAND,
	/** The wallet's first unconditional tier. */
	WALLET_BASE,
	/** The TNA-equivalent derived from a fund class's two latest cuotaparte quotes. */
	FUND_TNA_EQUIVALENT,
	/** The TNA-equivalent of a crypto APY for daily compounding. */
	APY_TNA_EQUIVALENT
}
