package ar.rindetuplata.api.ranking;

import ar.rindetuplata.api.instrument.ValidationFlagView;
import java.util.List;

/**
 * The outcome of simulating one instrument by id (T5c): {@code status} is the discriminator, and exactly one of
 * {@code entry}, {@code ineligible} or {@code excluded} carries the payload. An unknown id never reaches here; the
 * controller maps it to a 404 instead.
 */
public record SimulationResponse(String status, RankedEntryView entry, IneligibleView ineligible,
		ExcludedView excluded) {

	public static final String SIMULATED = "SIMULATED";
	public static final String INELIGIBLE = "INELIGIBLE";
	public static final String EXCLUDED = "EXCLUDED";

	public static SimulationResponse simulated(RankedEntryView entry) {
		return new SimulationResponse(SIMULATED, entry, null, null);
	}

	public static SimulationResponse ineligible(IneligibleView ineligible) {
		return new SimulationResponse(INELIGIBLE, null, ineligible, null);
	}

	public static SimulationResponse excluded(ExcludedView excluded) {
		return new SimulationResponse(EXCLUDED, null, null, excluded);
	}

	/** Why the requested amount or term does not reach the instrument. */
	public record IneligibleView(String id, String name, String reason, String detail) {
	}

	/** Why the instrument is not ranked at all, with the flags of the row that produced it. */
	public record ExcludedView(String id, String name, String reason, List<ValidationFlagView> flags) {
	}
}
