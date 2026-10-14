package ar.rindetuplata.api.ranking;

import ar.rindetuplata.api.instrument.ValidationFlagView;
import ar.rindetuplata.ranking.SourceView;
import java.time.LocalDate;
import java.util.List;

/**
 * An instrument left out of the ranking with its reason and the flags of the row that produced it (S15): the row
 * is returned apart, never silently dropped.
 */
public record ExcludedEntryView(String id, String kind, String name, SourceView source, LocalDate captureDate,
		LocalDate dataDate, String reason, List<ValidationFlagView> flags) {
}
