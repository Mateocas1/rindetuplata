package ar.rindetuplata.ranking;

import ar.rindetuplata.domain.Ineligible;
import java.time.LocalDate;
import java.util.Objects;

/** An instrument the requested amount or term does not reach, with the domain reason and its detail (S15). */
public record IneligibleEntry(String id, RankedKind kind, String name, SourceView source, LocalDate captureDate,
		LocalDate dataDate, Ineligible ineligible) {

	public IneligibleEntry {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(captureDate, "captureDate");
		Objects.requireNonNull(dataDate, "dataDate");
		Objects.requireNonNull(ineligible, "ineligible");
	}
}
