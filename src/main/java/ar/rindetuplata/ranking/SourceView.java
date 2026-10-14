package ar.rindetuplata.ranking;

import ar.rindetuplata.source.SourceRef;
import java.util.Objects;

/** Where a ranked row comes from (S11), as the T3 source declares itself. */
public record SourceView(String id, String displayName, boolean official) {

	public SourceView {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(displayName, "displayName");
	}

	public static SourceView of(SourceRef source) {
		return new SourceView(source.id(), source.displayName(), source.official());
	}
}
