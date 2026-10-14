package ar.rindetuplata.ranking;

import ar.rindetuplata.domain.AnnualRate;
import ar.rindetuplata.domain.ConditionalRate;
import ar.rindetuplata.domain.Inflation;
import ar.rindetuplata.domain.Money;
import ar.rindetuplata.domain.RateTier;
import ar.rindetuplata.source.plazofijo.TermRate;
import ar.rindetuplata.source.wallet.WalletCondition;
import ar.rindetuplata.source.wallet.WalletTier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Pure mappings from stored source shapes to {@code domain} instruments (T5 design notes). No Spring, no SQL:
 * every rule here is covered by a plain unit test with hand-computed values.
 */
final class InstrumentMapper {

	private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(AnnualRate.DAY_COUNT);
	private static final AnnualRate ZERO_RATE = AnnualRate.ofFraction("0");

	private InstrumentMapper() {
	}

	/**
	 * Stored wallet tiers become a bracket chain covering the whole balance: a floor above zero or a gap between
	 * tiers earns nothing, a bounded top is closed with an "above 0%" bracket. An empty schedule maps to no tier.
	 */
	static List<RateTier> rateTiers(List<WalletTier> tiers) {
		if (tiers == null || tiers.isEmpty()) {
			return List.of();
		}
		List<WalletTier> ordered = new ArrayList<>(tiers);
		ordered.sort(Comparator.comparing(tier -> tier.floor() == null ? BigDecimal.ZERO : tier.floor()));
		List<RateTier> brackets = new ArrayList<>();
		BigDecimal cursor = BigDecimal.ZERO;
		for (WalletTier tier : ordered) {
			BigDecimal floor = tier.floor() == null ? BigDecimal.ZERO : tier.floor();
			if (floor.signum() > 0 && floor.compareTo(cursor) > 0) {
				brackets.add(RateTier.upTo(Money.ars(floor), ZERO_RATE));
				cursor = floor;
			}
			AnnualRate rate = rate(tier.tna());
			BigDecimal ceiling = tier.ceiling();
			if (ceiling == null) {
				brackets.add(RateTier.above(rate));
				return List.copyOf(brackets);
			}
			if (ceiling.compareTo(cursor) > 0) {
				brackets.add(RateTier.upTo(Money.ars(ceiling), rate));
				cursor = ceiling;
			}
		}
		brackets.add(RateTier.above(ZERO_RATE));
		return List.copyOf(brackets);
	}

	/** Stored wallet conditions become labeled "up to" schedules with the same bracket rules. */
	static List<ConditionalRate> conditionalRates(List<WalletCondition> conditions) {
		if (conditions == null || conditions.isEmpty()) {
			return List.of();
		}
		List<ConditionalRate> rates = new ArrayList<>(conditions.size());
		for (WalletCondition condition : conditions) {
			List<RateTier> tiers = rateTiers(condition.tiers());
			if (!tiers.isEmpty()) {
				rates.add(new ConditionalRate(condition.condition(), tiers));
			}
		}
		return List.copyOf(rates);
	}

	/** Crypto APY (compounded) as the TNA-equivalent for daily compounding: ((1 + APY)^(1/365) − 1) · 365. */
	static AnnualRate apyToTna(BigDecimal apy) {
		BigDecimal daily = Inflation.overHorizon(apy, 1);
		return new AnnualRate(daily.multiply(DAYS_IN_YEAR, Money.MATH));
	}

	/** The TNA of the first stored term band containing the request; {@code null} bands never match. */
	static Optional<BigDecimal> matchingTermTna(List<TermRate> terms, int days, Money amount) {
		if (terms == null) {
			return Optional.empty();
		}
		for (TermRate term : terms) {
			if (term.tna() == null || !withinDays(term, days) || !withinAmount(term, amount)) {
				continue;
			}
			return Optional.of(term.tna());
		}
		return Optional.empty();
	}

	private static boolean withinDays(TermRate term, int days) {
		return (term.minDays() == null || days >= term.minDays()) && (term.maxDays() == null || days <= term.maxDays());
	}

	private static boolean withinAmount(TermRate term, Money amount) {
		BigDecimal value = amount.value();
		return (term.minAmount() == null || value.compareTo(term.minAmount()) >= 0)
				&& (term.maxAmount() == null || value.compareTo(term.maxAmount()) <= 0);
	}

	private static AnnualRate rate(BigDecimal fraction) {
		return fraction == null ? ZERO_RATE : new AnnualRate(fraction);
	}
}
