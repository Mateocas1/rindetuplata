package ar.rindetuplata.ranking;

import ar.rindetuplata.source.ValidationFlag;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** An instrument left out of the ranking with its reason and the flags of the row that produced it. */
public record ExcludedEntry(String id, RankedKind kind, String name, SourceView source, LocalDate captureDate,
		LocalDate dataDate, ExcludedReason reason, List<ValidationFlag> flags) {

	public ExcludedEntry {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(captureDate, "captureDate");
		Objects.requireNonNull(dataDate, "dataDate");
		Objects.requireNonNull(reason, "reason");
		flags = flags == null ? List.of() : List.copyOf(flags);
	}
}
