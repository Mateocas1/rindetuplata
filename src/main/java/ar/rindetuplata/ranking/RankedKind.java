package ar.rindetuplata.ranking;

/**
 * The four groups the saver ranking covers (T5a). Official BCRA plazo fijo rows are listed by the API but never
 * ranked in the MVP: their {@code plazoMinimoInvertirDias} is an undocumented term code, not days.
 */
public enum RankedKind {
	PLAZO_FIJO,
	WALLET,
	FUND,
	CRYPTO
}
