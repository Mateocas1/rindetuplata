package ar.rindetuplata.ranking;

import ar.rindetuplata.domain.FundYield;
import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.ValidationFlag;
import ar.rindetuplata.source.ValidationRules;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Fund class evaluation: the TNA-equivalent over the trailing
 * {@value ValidationRules#FUND_YIELD_WINDOW_DAYS}-day window. The yield runs from the newest non-invalid quote back to
 * the newest non-invalid one dated at least that many days earlier; when none is that old it runs back to the oldest
 * non-invalid quote available and carries a {@link FlagCode#SHORT_YIELD_WINDOW} warning. A class with fewer than two
 * valid quotes has no yield yet, one whose latest valid quote is more than {@value ValidationRules#FUND_STALE_DAYS}
 * days before the query date is stale, and a class whose cuotaparte fell has a negative return, no TNA-equivalent
 * and must never be ranked.
 */
final class FundEvaluator {

	record FundEvaluation(FundYield yield, LocalDate latestQuoteDate, List<ValidationFlag> warnings,
			ExcludedReason reason) {

		boolean eligible() {
			return reason == null;
		}
	}

	private FundEvaluator() {
	}

	/** {@code quotesDescending} is one class's quotes at or before the query date, newest first. */
	static FundEvaluation evaluate(List<RankingReader.StoredQuote> quotesDescending, LocalDate asOf) {
		List<RankingReader.StoredQuote> valid = quotesDescending.stream().filter(quote -> !quote.invalid()).toList();
		List<ValidationFlag> warnings = quotesDescending.isEmpty() ? List.of() : quotesDescending.get(0).flags();
		LocalDate newest = quotesDescending.isEmpty() ? asOf : quotesDescending.get(0).quoteDate();
		if (valid.size() < 2) {
			return new FundEvaluation(null, newest, warnings, ExcludedReason.INSUFFICIENT_HISTORY);
		}
		RankingReader.StoredQuote latest = valid.get(0);
		if (latest.quoteDate().plusDays(ValidationRules.FUND_STALE_DAYS).isBefore(asOf)) {
			return new FundEvaluation(null, latest.quoteDate(), latest.flags(), ExcludedReason.STALE);
		}
		LocalDate baseLimit = latest.quoteDate().minusDays(ValidationRules.FUND_YIELD_WINDOW_DAYS);
		RankingReader.StoredQuote base = valid.stream().skip(1)
				.filter(quote -> !quote.quoteDate().isAfter(baseLimit))
				.findFirst()
				.orElse(null);
		List<ValidationFlag> latestFlags = latest.flags();
		if (base == null) {
			base = valid.get(valid.size() - 1);
			latestFlags = new ArrayList<>(latestFlags);
			latestFlags.add(new ValidationFlag(FlagCode.SHORT_YIELD_WINDOW, Severity.WARNING,
					"Yield measured over " + ChronoUnit.DAYS.between(base.quoteDate(), latest.quoteDate())
							+ " days: no valid quote is " + ValidationRules.FUND_YIELD_WINDOW_DAYS + " days old"));
		}
		FundYield yield = new FundYield(base.quoteDate(), base.vcp(), latest.quoteDate(), latest.vcp());
		if (yield.periodReturn().signum() < 0) {
			return new FundEvaluation(yield, latest.quoteDate(), latestFlags, ExcludedReason.NEGATIVE_RETURN);
		}
		return new FundEvaluation(yield, latest.quoteDate(), latestFlags, null);
	}
}
