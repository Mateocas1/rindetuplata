package ar.rindetuplata.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How often a bank paid the highest non-client TNA among banks. Over the observations of a window, a day counts as
 * observed for a bank when it has a non-client rate that day, and as best when that rate equals the day's highest
 * among all banks: a tie counts for every bank tied. A day a bank has no rate (a gap, an invalid snapshot, a bank
 * that only quotes a client rate) is neither observed nor best for it, though it still counts for the others.
 */
public final class BankStanding {

	private BankStanding() {
	}

	/** One bank's non-client TNA captured on one day. */
	public record Observation(long instrumentId, LocalDate date, BigDecimal rate) {
	}

	/** {@code bestDays} out of {@code observedDays}. */
	public record Standing(int bestDays, int observedDays) {
	}

	public static Standing of(long instrumentId, List<Observation> observations) {
		Map<LocalDate, BigDecimal> highest = new HashMap<>();
		for (Observation observation : observations) {
			highest.merge(observation.date(), observation.rate(), BigDecimal::max);
		}
		int best = 0;
		int observed = 0;
		for (Observation observation : observations) {
			if (observation.instrumentId() != instrumentId) {
				continue;
			}
			observed++;
			if (observation.rate().compareTo(highest.get(observation.date())) == 0) {
				best++;
			}
		}
		return new Standing(best, observed);
	}
}
