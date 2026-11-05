package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.domain.Money;
import ar.rindetuplata.ranking.RankedEntry;
import ar.rindetuplata.ranking.RankedKind;
import ar.rindetuplata.ranking.RateKind;
import ar.rindetuplata.ranking.RateUsed;
import ar.rindetuplata.ranking.SourceView;
import ar.rindetuplata.source.Confidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * BEST_CHANGED and WEEKLY_DIGEST see the ranking the way the public page groups it. The classes of one
 * fund fold into the best one, so a change of class inside a fund is not a change of the best option, while two
 * different funds, or a fund and a wallet, are different options.
 */
class AlertRankingTest {

	private static final LocalDate DAY = LocalDate.parse("2026-10-14");

	@Test
	void theClassesOfOneFundAreOneOptionAndTheBestClassRepresentsIt() {
		RankedEntry classA = entry("10", RankedKind.FUND, "Fondo Uno - Clase A", "0.40");
		RankedEntry classB = entry("11", RankedKind.FUND, "Fondo Uno - Clase B", "0.39");
		RankedEntry other = entry("12", RankedKind.FUND, "Fondo Dos - Clase A", "0.38");

		List<RankedEntry> scope = AlertRanking.scope(rule(null), List.of(classA, classB, other));

		assertThat(scope).containsExactly(classA, other);
		assertThat(AlertRanking.key(classA)).as("a fund is its fund, not its class")
			.isEqualTo(AlertRanking.key(classB))
			.isEqualTo("fund:fondo uno");
		assertThat(AlertRanking.key(other)).isNotEqualTo(AlertRanking.key(classA));
	}

	@Test
	void aClassChangeInsideTheTopFundIsNotAChangeOfTheTop() {
		RankedEntry yesterday = entry("10", RankedKind.FUND, "Fondo Uno - Clase A", "0.40");
		RankedEntry today = entry("11", RankedKind.FUND, "Fondo Uno - Clase B", "0.42");

		assertThat(AlertRanking.key(AlertRanking.scope(rule(null), List.of(today, yesterday)).get(0)))
			.isEqualTo(AlertRanking.key(AlertRanking.scope(rule(null), List.of(yesterday)).get(0)));
	}

	@Test
	void anythingThatIsNotAFundIsItsInstrument() {
		RankedEntry wallet = entry("20", RankedKind.WALLET, "Fondo Uno - Clase A", "0.40");

		assertThat(AlertRanking.key(wallet)).as("an FCI-backed wallet is a wallet, whatever its name").isEqualTo("id:20");
	}

	@Test
	void theKindFilterKeepsRankingOrderAndDropsTheOtherKinds() {
		RankedEntry wallet = entry("20", RankedKind.WALLET, "Wallet", "0.50");
		RankedEntry fund = entry("10", RankedKind.FUND, "Fondo Uno - Clase A", "0.40");
		RankedEntry crypto = entry("30", RankedKind.CRYPTO, "Cripto", "0.30");

		assertThat(AlertRanking.scope(rule(RankedKind.FUND), List.of(wallet, fund, crypto))).containsExactly(fund);
		assertThat(AlertRanking.scope(rule(null), List.of(wallet, fund, crypto))).containsExactly(wallet, fund, crypto);
	}

	private static AlertRule rule(RankedKind kind) {
		return new AlertRule(1, 1, "saver@example.com", AlertType.BEST_CHANGED, kind, null, null, true,
				Instant.parse("2026-10-14T12:00:00Z"));
	}

	private static RankedEntry entry(String id, RankedKind kind, String name, String tna) {
		BigDecimal rate = new BigDecimal(tna);
		return new RankedEntry(id, kind, name, "Entity", new SourceView("src", "Source", false),
				Confidence.AGGREGATOR_AUTOMATIC, null, DAY, DAY, RateUsed.tna(rate, RateKind.FUND_TNA_EQUIVALENT),
				Money.ars("1000000"), Money.ars("0"), BigDecimal.ZERO, null, null, null, List.of(), 0, null);
	}

}
