package ar.rindetuplata.api.ranking;

import ar.rindetuplata.ranking.SourceView;
import java.time.LocalDate;

/**
 * An instrument the requested amount or term does not reach (S15): never dropped, always returned with the domain
 * reason ({@code MINIMUM_TERM} or {@code MINIMUM_AMOUNT}) and a plain-language detail.
 */
public record IneligibleEntryView(String id, String kind, String name, SourceView source, LocalDate captureDate,
		LocalDate dataDate, String reason, String detail) {
}
