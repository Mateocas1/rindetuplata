package ar.rindetuplata.ranking;

import ar.rindetuplata.source.wallet.FundClassKey;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Which fund classes the ranking cannot offer to the general public, decided from the class name alone because the
 * source publishes no currency or eligibility field (L7): classes in a foreign currency, whose cuotaparte is not
 * pesos, and the tax-regularization classes of Ley 27.743.
 */
final class FundClassRules {

	/** Whole words on a normalized key (lower case, no accents): USD, U$S, dolar, dolares. */
	private static final Pattern FOREIGN_CURRENCY = Pattern
			.compile("(?<![\\p{L}\\p{N}$])(?:usd|u\\$s|dolar|dolares)(?![\\p{L}\\p{N}$])");

	/** "Ley N° 27.743", "Ley Nº 27.743", "Ley 27743": the number sign is a degree or an ordinal sign, or absent. */
	private static final Pattern TAX_AMNESTY = Pattern.compile("\\bley\\s*(?:n[°º.]?\\s*)?27\\.?743\\b");

	private FundClassRules() {
	}

	/** The reason this class is not ranked, if any. The currency is read from the fund name, not the class suffix. */
	static Optional<ExcludedReason> exclusion(String className) {
		if (FOREIGN_CURRENCY.matcher(FundClassKey.fundOf(className)).find()) {
			return Optional.of(ExcludedReason.FOREIGN_CURRENCY);
		}
		if (TAX_AMNESTY.matcher(FundClassKey.of(className)).find()) {
			return Optional.of(ExcludedReason.RESTRICTED_CLASS);
		}
		return Optional.empty();
	}
}
