package ar.rindetuplata.ranking;

import ar.rindetuplata.domain.FundYield;
import ar.rindetuplata.source.ValidationFlag;
import ar.rindetuplata.source.ValidationRules;
import java.time.LocalDate;
import java.util.List;

/**
 * Fund class evaluation (T5 design notes): the TNA-equivalent of the two latest non-invalid quotes. A class with
 * fewer than two quotes has no yield yet, one whose latest quote is more than
 * {@value ValidationRules#FUND_STALE_DAYS} days before the query date is stale, and a class whose cuotaparte fell
 * has a negative return, no TNA-equivalent and must never be ranked (T4 note).
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
		RankingReader.StoredQuote previous = valid.get(1);
		FundYield yield = new FundYield(previous.quoteDate(), previous.vcp(), latest.quoteDate(), latest.vcp());
		if (yield.periodReturn().signum() < 0) {
			return new FundEvaluation(yield, latest.quoteDate(), latest.flags(), ExcludedReason.NEGATIVE_RETURN);
		}
		return new FundEvaluation(yield, latest.quoteDate(), latest.flags(), null);
	}
}
