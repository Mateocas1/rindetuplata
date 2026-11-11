package ar.rindetuplata.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ar.rindetuplata.domain.Money;
import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Segment;
import ar.rindetuplata.source.fund.ArgentinaDatosFundAdapter;
import ar.rindetuplata.source.fund.FundQuote;
import ar.rindetuplata.store.FundQuoteWriter;
import ar.rindetuplata.store.StoreTestConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Personal Pay is an FCI-backed wallet of the class "Delta Pesos - Clase X". The quotes are synthetic:
 * the rules under test are the mapping and the once-only rule, not the numbers of a recorded day.
 */
@Import(StoreTestConfiguration.class)
@SpringBootTest
@Transactional
class RankingFciWalletsTest {

	static final Instant FETCHED = Instant.parse("2026-10-14T19:06:23Z");
	static final LocalDate ASOF = LocalDate.parse("2026-10-14");
	static final LocalDate DAY_BEFORE = LocalDate.parse("2026-10-13");

	@Autowired
	FundQuoteWriter fundWriter;

	@Autowired
	RankingService service;

	@Test
	void personalPayIsRankedAtTheYieldOfDeltaPesosClaseX() {
		seedFund("Delta Pesos - Clase X", DAY_BEFORE, "1000");
		seedFund("Delta Pesos - Clase X", ASOF, "1000.5");

		RankingResult result = service.rank(query(RankedKind.WALLET));

		assertThat(result.ranked()).hasSize(1);
		RankedEntry entry = result.ranked().get(0);
		assertThat(entry.kind()).isEqualTo(RankedKind.WALLET);
		assertThat(entry.name()).isEqualTo("Personal Pay");
		assertThat(entry.entity()).isEqualTo("Delta Pesos - Clase X");
		assertThat(entry.confidence()).isEqualTo(Confidence.AGGREGATOR_AUTOMATIC);
		assertThat(entry.rateUsed().kind()).isEqualTo(RateKind.FUND_TNA_EQUIVALENT);
		assertThat(entry.rateUsed().fraction()).isEqualByComparingTo("0.1825");
		assertThat(entry.finalAmount().presented()).isEqualByComparingTo("1015109.26");
	}

	@Test
	void personalPayIsShownOnceWhenWalletsAndFundsAreRankedAndItsClassIsNotAlsoAFund() {
		seedFund("Delta Pesos - Clase X", DAY_BEFORE, "1000");
		seedFund("Delta Pesos - Clase X", ASOF, "1000.5");
		seedFund("Fondo Test - Clase A", DAY_BEFORE, "1000");
		seedFund("Fondo Test - Clase A", ASOF, "1000.4");

		RankingResult result = service.rank(query(RankedKind.WALLET, RankedKind.FUND));

		assertThat(result.ranked()).extracting(RankedEntry::kind, RankedEntry::name).containsExactly(
				tuple(RankedKind.WALLET, "Personal Pay"), tuple(RankedKind.FUND, "Fondo Test - Clase A"));
		assertThat(result.ranked()).extracting(RankedEntry::name).doesNotContain("Delta Pesos - Clase X");
		assertThat(result.totals().ranked()).isEqualTo(2);
	}

	@Test
	void anotherClassOfTheSameFundStaysAFundRow() {
		seedFund("Delta Pesos - Clase X", DAY_BEFORE, "1000");
		seedFund("Delta Pesos - Clase X", ASOF, "1000.5");
		seedFund("Delta Pesos - Clase A", DAY_BEFORE, "1000");
		seedFund("Delta Pesos - Clase A", ASOF, "1000.3");

		RankingResult result = service.rank(query(RankedKind.WALLET, RankedKind.FUND));

		assertThat(result.ranked()).extracting(RankedEntry::kind, RankedEntry::name).containsExactly(
				tuple(RankedKind.WALLET, "Personal Pay"), tuple(RankedKind.FUND, "Delta Pesos - Clase A"));
	}

	@Test
	void personalPayWhoseClassIsAbsentReadsFundClassNotFound() {
		seedFund("Fondo Test - Clase A", DAY_BEFORE, "1000");
		seedFund("Fondo Test - Clase A", ASOF, "1000.4");

		RankingResult result = service.rank(query(RankedKind.WALLET, RankedKind.FUND));

		assertThat(result.excluded()).filteredOn(e -> e.name().equals("Personal Pay"))
				.extracting(ExcludedEntry::kind, ExcludedEntry::reason)
				.containsExactly(tuple(RankedKind.WALLET, ExcludedReason.FUND_CLASS_NOT_FOUND));
		assertThat(result.ranked()).extracting(RankedEntry::name).containsExactly("Fondo Test - Clase A");
	}

	@Test
	void personalPayWithOneQuoteIsInsufficientHistoryNotAMissingClass() {
		seedFund("Delta Pesos - Clase X", ASOF, "1000.5");

		RankingResult result = service.rank(query(RankedKind.WALLET, RankedKind.FUND));

		assertThat(result.excluded()).filteredOn(e -> e.name().equals("Personal Pay"))
				.extracting(ExcludedEntry::reason).containsExactly(ExcludedReason.INSUFFICIENT_HISTORY);
		assertThat(result.ranked()).isEmpty();
	}

	private static RankingQuery query(RankedKind... kinds) {
		return new RankingQuery(Money.ars("1000000"), 30, Set.of(), Set.of(kinds), ASOF, 50);
	}

	private void seedFund(String name, LocalDate quoteDate, String vcp) {
		int separator = name.indexOf(" - ");
		FundQuote quote = new FundQuote(ArgentinaDatosFundAdapter.SOURCE, name, name.substring(0, separator),
				name.substring(separator + 3), "corto", Segment.SAVER, quoteDate, new BigDecimal(vcp), null, null,
				FETCHED, Confidence.AGGREGATOR_AUTOMATIC, List.of());
		fundWriter.write(List.of(quote), quoteDate);
	}
}
