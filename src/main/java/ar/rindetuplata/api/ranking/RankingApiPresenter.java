package ar.rindetuplata.api.ranking;

import ar.rindetuplata.api.JsonScales;
import ar.rindetuplata.api.instrument.ValidationFlagView;
import ar.rindetuplata.ranking.ExcludedEntry;
import ar.rindetuplata.ranking.IneligibleEntry;
import ar.rindetuplata.ranking.InflationView;
import ar.rindetuplata.ranking.RankedEntry;
import ar.rindetuplata.ranking.RankingResult;
import ar.rindetuplata.ranking.SimulationResult;
import ar.rindetuplata.source.ValidationFlag;
import java.util.Comparator;
import java.util.List;

/**
 * The JSON presentation of the T5a ranking records (S15, S10): money at scale 2 and rates and returns at scale 6
 * {@code HALF_EVEN} through {@link JsonScales}, dates ISO, and the source and flags of every row (S11). The
 * request echo names the effective defaults, so a caller sees the amount, term and kinds that were actually used.
 */
final class RankingApiPresenter {

	private RankingApiPresenter() {
	}

	static RankingResponse ranking(RankingResult result) {
		return new RankingResponse(
				request(result),
				result.inflation() == null ? null : inflation(result.inflation()),
				result.ranked().stream().map(RankingApiPresenter::entry).toList(),
				result.ineligible().stream().map(RankingApiPresenter::ineligible).toList(),
				result.excluded().stream().map(RankingApiPresenter::excluded).toList(),
				new TotalsView(result.totals().ranked(), result.totals().ineligible(), result.totals().excluded()));
	}

	static SimulationResponse simulation(SimulationResult result) {
		return switch (result) {
			case SimulationResult.Simulated simulated -> SimulationResponse.simulated(entry(simulated.entry()));
			case SimulationResult.IneligibleInstrument ineligible -> SimulationResponse.ineligible(
					new SimulationResponse.IneligibleView(ineligible.id(), ineligible.name(),
							ineligible.ineligible().reason().name(), ineligible.ineligible().detail()));
			case SimulationResult.ExcludedInstrument excluded -> SimulationResponse.excluded(
					new SimulationResponse.ExcludedView(excluded.id(), excluded.name(),
							excluded.reason().name(), flags(excluded.flags())));
		};
	}

	private static RankingRequestView request(RankingResult result) {
		List<String> clientOf = result.query().clientOf().stream().map(String::valueOf).sorted().toList();
		List<String> kinds = result.query().kinds().stream().map(Enum::name).sorted().toList();
		return new RankingRequestView(JsonScales.money(result.query().amount().presented()), result.query().days(),
				clientOf, kinds, result.query().asOf(), result.query().limit());
	}

	private static InflationResponse inflation(InflationView inflation) {
		return new InflationResponse(inflation.index(), JsonScales.rate(inflation.twelveMonthRate()),
				inflation.observationDate(), JsonScales.rate(inflation.overHorizon()));
	}

	static RankedEntryView entry(RankedEntry entry) {
		return new RankedEntryView(
				entry.id(),
				entry.kind().name(),
				entry.name(),
				entry.entity(),
				entry.source(),
				entry.confidence().name(),
				entry.verifiedAt(),
				entry.captureDate(),
				entry.dataDate(),
				new RateUsedView(JsonScales.rate(entry.rateUsed().fraction()), entry.rateUsed().basis(),
						entry.rateUsed().kind().name()),
				JsonScales.money(entry.finalAmount().presented()),
				JsonScales.money(entry.earned().presented()),
				JsonScales.rate(entry.nominalReturn()),
				JsonScales.rate(entry.realReturn()),
				entry.upTo() == null ? null
						: new UpToView(entry.upTo().condition(),
								JsonScales.money(entry.upTo().finalAmount().presented())),
				flags(entry.warnings()));
	}

	private static IneligibleEntryView ineligible(IneligibleEntry entry) {
		return new IneligibleEntryView(entry.id(), entry.kind().name(), entry.name(), entry.source(),
				entry.captureDate(), entry.dataDate(), entry.ineligible().reason().name(),
				entry.ineligible().detail());
	}

	private static ExcludedEntryView excluded(ExcludedEntry entry) {
		return new ExcludedEntryView(entry.id(), entry.kind().name(), entry.name(), entry.source(),
				entry.captureDate(), entry.dataDate(), entry.reason().name(), flags(entry.flags()));
	}

	private static List<ValidationFlagView> flags(List<ValidationFlag> flags) {
		if (flags == null) {
			return List.of();
		}
		return flags.stream()
				.sorted(Comparator.comparing((ValidationFlag flag) -> flag.severity().name())
						.thenComparing(flag -> flag.code().name()))
				.map(flag -> new ValidationFlagView(flag.code().name(), flag.severity().name(), flag.message()))
				.toList();
	}
}
