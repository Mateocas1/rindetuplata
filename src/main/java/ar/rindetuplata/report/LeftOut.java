package ar.rindetuplata.report;

import ar.rindetuplata.ranking.RankedKind;
import java.util.Objects;

/** An instrument the month's report could not measure, with the reason. */
public record LeftOut(String id, RankedKind kind, String name, String entity, String sourceId,
		LeftOutReason reason) {

	public LeftOut {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(sourceId, "sourceId");
		Objects.requireNonNull(reason, "reason");
	}
}
