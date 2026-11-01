package ar.rindetuplata.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.rindetuplata.domain.Money;
import ar.rindetuplata.ranking.InstrumentNotFoundException;
import ar.rindetuplata.ranking.RankingQuery;
import ar.rindetuplata.ranking.RankingResult;
import ar.rindetuplata.ranking.RankingService;
import ar.rindetuplata.ranking.SimulationResult;
import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Segment;
import ar.rindetuplata.source.fund.ArgentinaDatosFundAdapter;
import ar.rindetuplata.source.fund.FundQuote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T3a rule (S3, L22): the targeted {@code simulate()} answers exactly what the full basket answers, for every
 * instrument id. The reference is {@link RankingService#rankAll}, which is {@code rank} over every kind with fund
 * classes ungrouped and no page cap (the public page tops out at {@link RankingQuery#MAX_LIMIT}, the recorded
 * day ranks about 400). The data is the recorded 2026-10-14 fixtures plus hand-made classes for the cases the
 * fixtures lack: a second class under the name an FCI-backed wallet maps to (the fixture's own class wins the
 * wallet by id, the duplicate is hidden everywhere), a class with a short history, a class with one quote only, and REM inflation so the real return is present.
 */
class SimulationEquivalenceApiTest extends RankingApiIntegrationTest {

	private static final LocalDate AS_OF = ApiTestConfiguration.TODAY;

	@Autowired
	RankingService service;

	@Test
	void everyInstrumentIdAnswersWhatTheFullBasketAnswersWhenTheMappedWalletIsStale() {
		seedMixedBasket();

		assertEveryIdEquivalent();
		SimulationResult wallet = fullBasket(new Scenario(Money.ars("1000000"), 30, false), Set.of())
				.get(String.valueOf(classId("Mercado Fondo - Clase A")));
		assertThat(wallet).as("the fixture's mapped class is stale: the wallet is excluded under its own name")
				.isInstanceOfSatisfying(SimulationResult.ExcludedInstrument.class,
						excluded -> assertThat(excluded.name()).isEqualTo("Mercado Pago"));
	}

	@Test
	void everyInstrumentIdAnswersWhatTheFullBasketAnswersWhenTheMappedWalletIsRanked() {
		seedMixedBasket();
		seedClass("Mercado Fondo - Clase A", "1000", "1003");

		assertEveryIdEquivalent();
	}

	/** The recorded day, REM inflation and the hand-made classes the fixtures lack. */
	private void seedMixedBasket() {
		seedAll();
		seedRem("0.25", CAPTURE);
		// The fixture already holds the mapped class; a second class of the same normalized name is hidden.
		seedClass("MERCADO FONDO - CLASE A", "1000", "1002");
		seedClass("Fondo Corto - Clase A", "1000", "1000.5");
		seedOneQuoteClass("Fondo Nuevo - Clase A");
	}

	private void assertEveryIdEquivalent() {
		long onlyOneQuote = classId("Fondo Nuevo - Clase A");
		List<Long> ids = jdbc.sql("select id from instrument order by id").query(Long.class).list();
		assertThat(ids).hasSizeGreaterThan(100).contains(onlyOneQuote);

		int compared = 0;
		for (Scenario scenario : scenarios()) {
			Map<String, SimulationResult> full = fullBasket(scenario, Set.of());
			for (long id : ids) {
				assertSameAnswer(id, scenario, false, full.get(String.valueOf(id)));
				compared++;
			}
			if (scenario.client()) {
				for (long id : bankIds()) {
					assertSameAnswer(id, scenario, true, fullBasket(scenario, Set.of(id)).get(String.valueOf(id)));
					compared++;
				}
			}
		}
		assertThat(compared).isGreaterThan(ids.size() * scenarios().size());
	}

	@Test
	void anIdNoRankedInstrumentCarriesIsNotFound() {
		seedAll();
		long last = jdbc.sql("select max(id) from instrument").query(Long.class).single();

		for (long unknown : new long[] { 0, -1, last + 1000, Long.MAX_VALUE }) {
			assertThatThrownBy(() -> service.simulate(unknown, Money.ars("1000000"), 30, false, AS_OF))
					.isInstanceOf(InstrumentNotFoundException.class);
		}
	}

	@Test
	void theFixtureCoversEveryKindOfAnswer() {
		seedAll();
		seedRem("0.25", CAPTURE);
		seedClass("Mercado Fondo - Clase A", "1000", "1003");
		seedClass("MERCADO FONDO - CLASE A", "1000", "1002");
		seedClass("Fondo Corto - Clase A", "1000", "1000.5");
		seedOneQuoteClass("Fondo Nuevo - Clase A");

		Map<String, SimulationResult> full = fullBasket(new Scenario(Money.ars("1000000"), 30, false), Set.of());
		Map<String, SimulationResult> tooShort = fullBasket(new Scenario(Money.ars("1000000"), 29, false), Set.of());

		assertThat(tooShort.values()).anyMatch(SimulationResult.IneligibleInstrument.class::isInstance);
		assertThat(full.values()).anyMatch(SimulationResult.Simulated.class::isInstance);
		assertThat(full.values()).anyMatch(SimulationResult.ExcludedInstrument.class::isInstance);
		assertThat(full.values()).filteredOn(SimulationResult.Simulated.class::isInstance)
				.map(result -> ((SimulationResult.Simulated) result).entry().kind().name())
				.contains("PLAZO_FIJO", "WALLET", "FUND", "CRYPTO");
		Long winner = classId("Mercado Fondo - Clase A");
		Long hidden = classId("MERCADO FONDO - CLASE A");
		assertThat(full.get(String.valueOf(winner))).isInstanceOfSatisfying(SimulationResult.Simulated.class,
				simulated -> assertThat(simulated.entry().name()).as("the class shows as the wallet")
						.isEqualTo("Mercado Pago"));
		assertThat(full).as("a second class of the mapped name is hidden everywhere")
				.doesNotContainKey(String.valueOf(hidden));
	}

	private Long classId(String name) {
		return jdbc.sql("select id from instrument where kind = 'FUND_CLASS' and display_name = :name")
				.param("name", name).query(Long.class).single();
	}

	private void assertSameAnswer(long id, Scenario scenario, boolean client, SimulationResult expected) {
		String context = "instrument " + id + " for " + scenario + (client ? " as a client" : "");
		if (expected == null) {
			assertThatThrownBy(() -> service.simulate(id, scenario.amount(), scenario.days(), client, AS_OF))
					.as(context).isInstanceOf(InstrumentNotFoundException.class);
			return;
		}
		SimulationResult actual = service.simulate(id, scenario.amount(), scenario.days(), client, AS_OF);
		assertThat(actual).as(context).usingRecursiveComparison()
				.withComparatorForType(BigDecimal::compareTo, BigDecimal.class).isEqualTo(expected);
	}

	/** What the full basket says about each id: a candidate wins over an exclusion, as in the old simulate. */
	private Map<String, SimulationResult> fullBasket(Scenario scenario, Set<Long> clientOf) {
		RankingResult result = service.rankAll(new RankingQuery(scenario.amount(), scenario.days(), clientOf, Set.of(),
				AS_OF, RankingQuery.DEFAULT_LIMIT, false));
		Map<String, SimulationResult> byId = new HashMap<>();
		result.excluded().forEach(entry -> byId.put(entry.id(),
				new SimulationResult.ExcludedInstrument(entry.id(), entry.name(), entry.reason(), entry.flags())));
		result.ineligible().forEach(entry -> byId.put(entry.id(),
				new SimulationResult.IneligibleInstrument(entry.id(), entry.name(), entry.ineligible())));
		result.ranked().forEach(entry -> byId.put(entry.id(), new SimulationResult.Simulated(entry, result.dollarMep())));
		return byId;
	}

	private List<Long> bankIds() {
		return jdbc.sql("select id from instrument where kind = 'PLAZO_FIJO_BANK' order by id")
				.query(Long.class).list();
	}

	private static List<Scenario> scenarios() {
		List<Scenario> scenarios = new ArrayList<>();
		scenarios.add(new Scenario(Money.ars("1000000"), 30, true));
		scenarios.add(new Scenario(Money.ars("1000000"), 29, false));
		scenarios.add(new Scenario(Money.ars("50000"), 90, true));
		scenarios.add(new Scenario(Money.ars("250000000"), 365, false));
		return scenarios;
	}

	private record Scenario(Money amount, int days, boolean client) {
	}

	private void seedClass(String name, String previousVcp, String latestVcp) {
		write(name, CAPTURE.minusDays(8), previousVcp);
		write(name, CAPTURE, latestVcp);
		recordIngestionRun();
	}

	private long seedOneQuoteClass(String name) {
		write(name, CAPTURE, "1000");
		recordIngestionRun();
		return jdbc.sql("select id from instrument where kind = 'FUND_CLASS' and display_name = :name")
				.param("name", name).query(Long.class).single();
	}

	private void write(String name, LocalDate date, String vcp) {
		int separator = name.indexOf(" - ");
		fundWriter.write(List.of(new FundQuote(ArgentinaDatosFundAdapter.SOURCE, name, name.substring(0, separator),
				name.substring(separator + 3), "corto", Segment.SAVER, date, new BigDecimal(vcp), null, null, FETCHED,
				Confidence.AGGREGATOR_AUTOMATIC, List.of())), date);
	}
}
