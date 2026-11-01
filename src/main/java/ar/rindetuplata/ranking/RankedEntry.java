package ar.rindetuplata.ranking;

import ar.rindetuplata.domain.ConditionalOutcome;
import ar.rindetuplata.domain.Money;
import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.ValidationFlag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One ranked instrument: everything the API needs to render a row without another query (S11, S15). {@code earned}
 * and {@code finalAmount} are the personalized outcome; {@code realReturn} is null when no REM observation exists;
 * {@code upTo} is the best conditional outcome, reported as "up to" and never used for the ranking.
 * {@code foldedClasses} is how many sibling classes of the same fund the grouped ranking folded into this row
 * (the best class for the request); it is 0 for every other kind and whenever grouping is off.
 */
public record RankedEntry(String id, RankedKind kind, String name, String entity, SourceView source,
		Confidence confidence, LocalDate verifiedAt, LocalDate captureDate, LocalDate dataDate, RateUsed rateUsed,
		Money finalAmount, Money earned, BigDecimal nominalReturn, BigDecimal realReturn, ConditionalOutcome upTo,
		List<ValidationFlag> warnings, int foldedClasses) {

	public RankedEntry {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(confidence, "confidence");
		Objects.requireNonNull(captureDate, "captureDate");
		Objects.requireNonNull(dataDate, "dataDate");
		Objects.requireNonNull(rateUsed, "rateUsed");
		Objects.requireNonNull(finalAmount, "finalAmount");
		Objects.requireNonNull(earned, "earned");
		Objects.requireNonNull(nominalReturn, "nominalReturn");
		if (foldedClasses < 0) {
			throw new IllegalArgumentException("foldedClasses must not be negative: " + foldedClasses);
		}
		warnings = warnings == null ? List.of() : List.copyOf(warnings);
	}

	RankedEntry withFoldedClasses(int folded) {
		return new RankedEntry(id, kind, name, entity, source, confidence, verifiedAt, captureDate, dataDate,
				rateUsed, finalAmount, earned, nominalReturn, realReturn, upTo, warnings, folded);
	}
}
