package ar.rindetuplata.ranking;

import ar.rindetuplata.domain.Ineligible;
import ar.rindetuplata.source.ValidationFlag;
import java.util.List;
import java.util.Objects;

/** The outcome of simulating one instrument by id: a ranked entry, an ineligible reason or an exclusion. */
public sealed interface SimulationResult {

	record Simulated(RankedEntry entry) implements SimulationResult {

		public Simulated {
			Objects.requireNonNull(entry, "entry");
		}
	}

	record IneligibleInstrument(String id, String name, Ineligible ineligible) implements SimulationResult {

		public IneligibleInstrument {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(ineligible, "ineligible");
		}
	}

	record ExcludedInstrument(String id, String name, ExcludedReason reason, List<ValidationFlag> flags)
			implements SimulationResult {

		public ExcludedInstrument {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(reason, "reason");
			flags = flags == null ? List.of() : List.copyOf(flags);
		}
	}
}
