package ar.rindetuplata.api.ranking;

import ar.rindetuplata.api.instrument.ValidationFlagView;
import ar.rindetuplata.ranking.SourceView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One ranked instrument as {@code GET /api/v1/ranking} returns it (S11, S15): the personalized outcome
 * ({@code finalAmount}, {@code earned}, {@code nominalReturn} and, when a REM observation exists, {@code realReturn})
 * with the rate actually used, the dates and source of the datum, its confidence and its WARNING flags.
 * {@code realReturn} and {@code upTo} are null when the caller has to show "no data" instead of inventing one.
 */
public record RankedEntryView(
		String id,
		String kind,
		String name,
		String entity,
		SourceView source,
		String confidence,
		LocalDate verifiedAt,
		LocalDate captureDate,
		LocalDate dataDate,
		RateUsedView rateUsed,
		BigDecimal finalAmount,
		BigDecimal earned,
		BigDecimal nominalReturn,
		BigDecimal realReturn,
		UpToView upTo,
		List<ValidationFlagView> warnings) {
}
