package ar.rindetuplata.api.instrument;

import ar.rindetuplata.api.JsonScales;
import ar.rindetuplata.domain.AnnualRate;
import ar.rindetuplata.store.InstrumentKind;
import java.math.BigDecimal;

/**
 * Puts a stored rate on the effective annual basis (TEA) the history chart compares with annualized inflation. A TNA
 * compounds the way its instrument pays: a wallet every day, a plazo fijo every 30 days (its minimum term). A basis
 * that is already effective (APY for crypto, TEA for the official regime) is passed through unchanged.
 */
final class EffectiveRate {

	private static final String TNA = "TNA";
	private static final int WALLET_COMPOUNDING_DAYS = 1;
	private static final int PLAZO_FIJO_COMPOUNDING_DAYS = 30;

	private EffectiveRate() {
	}

	/** The TEA of a stored rate at output scale, or {@code null} when the snapshot has no rate. */
	static BigDecimal tea(InstrumentKind kind, String basis, BigDecimal rate) {
		if (rate == null) {
			return null;
		}
		if (!TNA.equals(basis)) {
			return JsonScales.rate(rate);
		}
		int periodDays = kind == InstrumentKind.WALLET ? WALLET_COMPOUNDING_DAYS : PLAZO_FIJO_COMPOUNDING_DAYS;
		return JsonScales.rate(new AnnualRate(rate).tea(periodDays));
	}
}
