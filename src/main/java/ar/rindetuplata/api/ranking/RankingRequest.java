package ar.rindetuplata.api.ranking;

import ar.rindetuplata.api.ApiException;
import ar.rindetuplata.domain.Money;
import ar.rindetuplata.ranking.RankedKind;
import ar.rindetuplata.ranking.RankingQuery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Turns the raw request parameters of the ranking endpoints into a validated {@link RankingQuery} (T5c). A value
 * the caller got wrong becomes an {@link ApiException} naming the parameter, so the shared advice answers a 400
 * {@code ProblemDetail} that says exactly which one to fix. Missing values take the S15 defaults: $1.000.000, 30
 * days, 50 rows, today in the injected clock's zone.
 */
final class RankingRequest {

	private RankingRequest() {
	}

	static RankingQuery query(BigDecimal amount, Integer days, List<Long> clientOf, List<RankedKind> kind,
			LocalDate asOf, Integer limit, Clock clock) {
		return new RankingQuery(amount(amount), days(days), clientOf == null ? Set.of() : Set.copyOf(clientOf),
				kind == null ? Set.of() : Set.copyOf(kind), asOf == null ? LocalDate.now(clock) : asOf, limit(limit));
	}

	static Money amount(BigDecimal amount) {
		if (amount == null) {
			return RankingQuery.DEFAULT_AMOUNT;
		}
		if (amount.signum() <= 0) {
			throw ApiException.badRequest("amount",
					"Parameter 'amount' must be greater than 0: " + amount.toPlainString());
		}
		return Money.ars(amount);
	}

	static int days(Integer days) {
		if (days == null) {
			return RankingQuery.DEFAULT_DAYS;
		}
		if (days < 1) {
			throw ApiException.badRequest("days", "Parameter 'days' must be at least 1: " + days);
		}
		return days;
	}

	static int limit(Integer limit) {
		if (limit == null) {
			return RankingQuery.DEFAULT_LIMIT;
		}
		if (limit < 1 || limit > RankingQuery.MAX_LIMIT) {
			throw ApiException.badRequest("limit",
					"Parameter 'limit' must be between 1 and " + RankingQuery.MAX_LIMIT + ": " + limit);
		}
		return limit;
	}
}
