package ar.rindetuplata.api.ranking;

import ar.rindetuplata.api.ApiException;
import ar.rindetuplata.api.SupportedDates;
import ar.rindetuplata.domain.Money;
import ar.rindetuplata.ranking.RankedKind;
import ar.rindetuplata.ranking.RankingQuery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Turns the raw request parameters of the ranking endpoints into a validated {@link RankingQuery}. A value
 * the caller got wrong becomes an {@link ApiException} naming the parameter, so the shared advice answers a 400
 * {@code ProblemDetail} that says exactly which one to fix. Missing values take the defaults: $1.000.000, 30
 * days, 50 rows, fund classes grouped, today in the injected clock's zone.
 */
final class RankingRequest {

	/** The longest integer or fraction part a rejected amount is echoed with in plain form. */
	private static final int MAX_PLAIN_DIGITS = 40;

	private RankingRequest() {
	}

	static RankingQuery query(BigDecimal amount, Integer days, List<Long> clientOf, List<RankedKind> kind,
			LocalDate asOf, Integer limit, Boolean groupFunds, Clock clock) {
		return new RankingQuery(amount(amount), days(days), clientOf(clientOf),
				kind == null ? Set.of() : Set.copyOf(kind),
				asOf == null ? LocalDate.now(clock) : SupportedDates.checked("asOf", asOf), limit(limit),
				groupFunds == null || groupFunds);
	}

	/** The client banks as a set; the raw list is counted first, so repeated ids cannot get past the cap. */
	static Set<Long> clientOf(List<Long> clientOf) {
		if (clientOf == null) {
			return Set.of();
		}
		if (clientOf.size() > RankingQuery.MAX_CLIENT_OF) {
			throw ApiException.badRequest("clientOf", "Parameter 'clientOf' must have at most "
					+ RankingQuery.MAX_CLIENT_OF + " values: " + clientOf.size());
		}
		return Set.copyOf(clientOf);
	}

	static Money amount(BigDecimal amount) {
		if (amount == null) {
			return RankingQuery.DEFAULT_AMOUNT;
		}
		if (amount.signum() <= 0) {
			throw ApiException.badRequest("amount",
					"Parameter 'amount' must be greater than 0: " + echo(amount));
		}
		if (amount.compareTo(RankingQuery.MAX_AMOUNT) > 0) {
			throw ApiException.badRequest("amount", "Parameter 'amount' must be at most "
					+ RankingQuery.MAX_AMOUNT.toPlainString() + ": " + echo(amount));
		}
		return Money.ars(amount);
	}

	/**
	 * The rejected value as the caller can read it. Spring parses {@code 1e999999999} as a valid {@code BigDecimal},
	 * whose plain form is a billion characters, so a value with more than {@value #MAX_PLAIN_DIGITS} digits on either
	 * side of the point is echoed in scientific notation instead.
	 */
	private static String echo(BigDecimal amount) {
		long integerDigits = (long) amount.precision() - amount.scale();
		return integerDigits > MAX_PLAIN_DIGITS || amount.scale() > MAX_PLAIN_DIGITS ? amount.toString()
				: amount.toPlainString();
	}

	static int days(Integer days) {
		if (days == null) {
			return RankingQuery.DEFAULT_DAYS;
		}
		if (days < 1 || days > RankingQuery.MAX_DAYS) {
			throw ApiException.badRequest("days",
					"Parameter 'days' must be between 1 and " + RankingQuery.MAX_DAYS + ": " + days);
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
