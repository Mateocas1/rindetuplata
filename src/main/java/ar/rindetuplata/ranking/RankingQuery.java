package ar.rindetuplata.ranking;

import ar.rindetuplata.domain.Money;
import ar.rindetuplata.domain.Ranking;
import ar.rindetuplata.source.ValidationRules;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Input of the personalized ranking (S15, T5 design notes): the user's amount and days, the banks the user is a
 * client of, the kinds to cover, the reference date and the page size. {@link #defaults()} is $1.000.000 for 30
 * days, every kind, today in {@code America/Argentina/Buenos_Aires} and 50 rows (max
 * {@value #MAX_LIMIT}).
 */
public record RankingQuery(Money amount, int days, Set<Long> clientOf, Set<RankedKind> kinds, LocalDate asOf,
		int limit) {

	public static final Money DEFAULT_AMOUNT = Ranking.DEFAULT_AMOUNT;
	public static final int DEFAULT_DAYS = Ranking.DEFAULT_DAYS;
	public static final int DEFAULT_LIMIT = 50;
	public static final int MAX_LIMIT = 200;

	public RankingQuery {
		Objects.requireNonNull(amount, "amount");
		if (!amount.isPositive()) {
			throw new IllegalArgumentException("amount must be positive: " + amount.presented());
		}
		if (days < 1) {
			throw new IllegalArgumentException("days must be positive: " + days);
		}
		clientOf = clientOf == null ? Set.of() : Set.copyOf(clientOf);
		kinds = kinds == null || kinds.isEmpty() ? allKinds() : Set.copyOf(kinds);
		asOf = asOf == null ? today() : asOf;
		if (limit < 1 || limit > MAX_LIMIT) {
			throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT + ": " + limit);
		}
	}

	public static RankingQuery defaults() {
		return new RankingQuery(DEFAULT_AMOUNT, DEFAULT_DAYS, Set.of(), allKinds(), today(), DEFAULT_LIMIT);
	}

	/** Today in the Argentine calendar, the reference date of every query that does not name one. */
	public static LocalDate today() {
		return LocalDate.now(ValidationRules.ARGENTINA);
	}

	private static Set<RankedKind> allKinds() {
		return Set.copyOf(EnumSet.allOf(RankedKind.class));
	}
}
