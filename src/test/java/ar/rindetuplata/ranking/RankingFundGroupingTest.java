package ar.rindetuplata.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ar.rindetuplata.domain.Money;
import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Segment;
import ar.rindetuplata.source.crypto.ArgentinaDatosCryptoAdapter;
import ar.rindetuplata.source.crypto.CryptoYield;
import ar.rindetuplata.source.fund.ArgentinaDatosFundAdapter;
import ar.rindetuplata.source.fund.FundQuote;
import ar.rindetuplata.source.plazofijo.ArgentinaDatosPlazoFijoAdapter;
import ar.rindetuplata.source.plazofijo.BankPlazoFijoRate;
import ar.rindetuplata.source.wallet.UalaWalletAdapter;
import ar.rindetuplata.source.wallet.WalletRate;
import ar.rindetuplata.source.wallet.WalletTier;
import ar.rindetuplata.store.BankPlazoFijoWriter;
import ar.rindetuplata.store.CryptoYieldWriter;
import ar.rindetuplata.store.FundQuoteWriter;
import ar.rindetuplata.store.InstrumentKind;
import ar.rindetuplata.store.StoreReader;
import ar.rindetuplata.store.StoreTestConfiguration;
import ar.rindetuplata.store.WalletRateWriter;
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
 * T2c rules (S2, L5, L7): the public ranking shows one row per fund, the best class for the request, with the number
 * of folded siblings; grouping happens before the page limit and never in {@code rankAll} or a simulation; classes in
 * a foreign currency and tax-amnesty classes are excluded per class with their own reason.
 */
@Import(StoreTestConfiguration.class)
@SpringBootTest
@Transactional
class RankingFundGroupingTest {

	static final Instant FETCHED = Instant.parse("2026-10-14T19:06:23Z");
	static final LocalDate ASOF = LocalDate.parse("2026-10-14");

	@Autowired
	FundQuoteWriter fundWriter;

	@Autowired
	BankPlazoFijoWriter bankWriter;

	@Autowired
	WalletRateWriter walletWriter;

	@Autowired
	CryptoYieldWriter cryptoWriter;

	@Autowired
	RankingService service;

	@Autowired
	StoreReader reader;

	@Test
	void threeClassesOfOneFundCollapseIntoTheBestOneAndCountTheFoldedSiblings() {
		seedFund("Fondo Uno - Clase B", "1000.4");
		seedFund("Fondo Uno - Clase A", "1000.5");
		seedFund("Fondo Uno - Clase C", "1000.3");

		RankingResult grouped = service.rank(query(true, 50, RankedKind.FUND));
		RankingResult flat = service.rank(query(false, 50, RankedKind.FUND));

		assertThat(grouped.ranked()).extracting(RankedEntry::name, RankedEntry::foldedClasses)
				.containsExactly(tuple("Fondo Uno - Clase A", 2));
		RankedEntry bestClass = flat.ranked().get(0);
		assertThat(bestClass.name()).isEqualTo("Fondo Uno - Clase A");
		assertThat(grouped.ranked().get(0).finalAmount()).isEqualTo(bestClass.finalAmount());
		assertThat(grouped.totals()).isEqualTo(new RankingTotals(1, 0, 0));
	}

	@Test
	void groupFundsFalseReturnsEveryClassWithNothingFolded() {
		seedFund("Fondo Uno - Clase B", "1000.4");
		seedFund("Fondo Uno - Clase A", "1000.5");
		seedFund("Fondo Uno - Clase C", "1000.3");

		RankingResult result = service.rank(query(false, 50, RankedKind.FUND));

		assertThat(result.ranked()).extracting(RankedEntry::name, RankedEntry::foldedClasses).containsExactly(
				tuple("Fondo Uno - Clase A", 0), tuple("Fondo Uno - Clase B", 0), tuple("Fondo Uno - Clase C", 0));
		assertThat(result.totals()).isEqualTo(new RankingTotals(3, 0, 0));
	}

	@Test
	void aFundWithoutAClassSuffixIsItsOwnFundAndDifferentFundsAreNotMerged() {
		seedFund("Fondo Solo", "1000.5");
		seedFund("Fondo Dos - Clase A", "1000.4");
		seedFund("Fondo Tres - Clase A", "1000.3");

		RankingResult result = service.rank(query(true, 50, RankedKind.FUND));

		assertThat(result.ranked()).extracting(RankedEntry::name, RankedEntry::foldedClasses).containsExactly(
				tuple("Fondo Solo", 0), tuple("Fondo Dos - Clase A", 0), tuple("Fondo Tres - Clase A", 0));
	}

	@Test
	void groupingHappensBeforeThePageLimit() {
		seedFund("Fondo Alto - Clase A", "1000.5");
		seedFund("Fondo Alto - Clase B", "1000.45");
		seedFund("Fondo Bajo - Clase A", "1000.3");

		RankingResult limited = service.rank(query(true, 2, RankedKind.FUND));
		RankingResult flat = service.rank(query(false, 2, RankedKind.FUND));

		assertThat(limited.ranked()).extracting(RankedEntry::name, RankedEntry::foldedClasses)
				.containsExactly(tuple("Fondo Alto - Clase A", 1), tuple("Fondo Bajo - Clase A", 0));
		assertThat(limited.totals().ranked()).isEqualTo(2);
		assertThat(flat.ranked()).extracting(RankedEntry::name)
				.containsExactly("Fondo Alto - Clase A", "Fondo Alto - Clase B");
		assertThat(flat.totals().ranked()).isEqualTo(3);
	}

	@Test
	void rankAllDoesNotGroupBecauseAlertRulesTargetASpecificClass() {
		seedFund("Fondo Uno - Clase A", "1000.5");
		seedFund("Fondo Uno - Clase B", "1000.4");

		RankingResult result = service.rankAll(query(true, 50, RankedKind.FUND));

		assertThat(result.ranked()).extracting(RankedEntry::name, RankedEntry::foldedClasses)
				.containsExactly(tuple("Fondo Uno - Clase A", 0), tuple("Fondo Uno - Clase B", 0));
		assertThat(result.totals().ranked()).isEqualTo(2);
	}

	@Test
	void aSimulationOfASiblingClassIsNotFoldedIntoTheBestOne() {
		// simulate() reads as of today, so the quotes are seeded around today.
		LocalDate today = RankingQuery.today();
		seedFund("Fondo Uno - Clase A", today, "1000.5");
		seedFund("Fondo Uno - Clase B", today, "1000.4");
		long classB = instrumentId(InstrumentKind.FUND_CLASS, "Fondo Uno - Clase B");

		SimulationResult result = service.simulate(classB, Money.ars("1000000"), 30, false);

		RankedEntry entry = ((SimulationResult.Simulated) result).entry();
		assertThat(entry.name()).isEqualTo("Fondo Uno - Clase B");
		assertThat(entry.foldedClasses()).isZero();
	}

	@Test
	void foreignCurrencyClassesAreExcludedPerClassAndPesoClassesStayRanked() {
		seedFund("Balanz Money Market USD - Clase A", "1000.5");
		seedFund("Balanz Money Market USD - Clase B", "1000.5");
		seedFund("Cocos Dólar Money Market - Clase A", "1000.5");
		seedFund("Delta Dólares - Clase A", "1000.5");
		seedFund("Fondo U$S Ahorro - Clase A", "1000.5");
		seedFund("Delta Pesos - Clase A", "1000.4");

		RankingResult result = service.rank(query(true, 50, RankedKind.FUND));

		assertThat(result.ranked()).extracting(RankedEntry::name).containsExactly("Delta Pesos - Clase A");
		assertThat(result.excluded()).extracting(ExcludedEntry::name, ExcludedEntry::reason).containsExactly(
				tuple("Balanz Money Market USD - Clase A", ExcludedReason.FOREIGN_CURRENCY),
				tuple("Balanz Money Market USD - Clase B", ExcludedReason.FOREIGN_CURRENCY),
				tuple("Cocos Dólar Money Market - Clase A", ExcludedReason.FOREIGN_CURRENCY),
				tuple("Delta Dólares - Clase A", ExcludedReason.FOREIGN_CURRENCY),
				tuple("Fondo U$S Ahorro - Clase A", ExcludedReason.FOREIGN_CURRENCY));
		assertThat(result.totals()).isEqualTo(new RankingTotals(1, 0, 5));
	}

	@Test
	void taxAmnestyClassesInEverySpellingAreRestricted() {
		seedFund("Fondo Fiscal - Clase L Ley N° 27.743", "1000.5");
		seedFund("Fondo Fiscal - Clase K Ley Nº 27.743", "1000.5");
		seedFund("Fondo Fiscal - Clase Ley N° 27.743", "1000.5");
		seedFund("Fondo Fiscal - Clase A", "1000.4");

		RankingResult result = service.rank(query(true, 50, RankedKind.FUND));

		assertThat(result.ranked()).extracting(RankedEntry::name, RankedEntry::foldedClasses)
				.containsExactly(tuple("Fondo Fiscal - Clase A", 0));
		assertThat(result.excluded()).extracting(ExcludedEntry::name, ExcludedEntry::reason).containsExactly(
				tuple("Fondo Fiscal - Clase K Ley Nº 27.743", ExcludedReason.RESTRICTED_CLASS),
				tuple("Fondo Fiscal - Clase L Ley N° 27.743", ExcludedReason.RESTRICTED_CLASS),
				tuple("Fondo Fiscal - Clase Ley N° 27.743", ExcludedReason.RESTRICTED_CLASS));
	}

	@Test
	void walletsPlazosFijosAndCryptoRowsAreTheSameWithOrWithoutGrouping() {
		seedBank("Banco A", "0.30");
		seedBank("Banco B", "0.20");
		walletWriter.write(List.of(new WalletRate(UalaWalletAdapter.SOURCE, "uala", "Ualá",
				List.of(new WalletTier(BigDecimal.ZERO, null, new BigDecimal("0.19"))), List.of(), ASOF, null,
				FETCHED, Confidence.SCRAPED_ENTITY_WEB, List.of())), ASOF);
		cryptoWriter.write(List.of(new CryptoYield(ArgentinaDatosCryptoAdapter.SOURCE, "belo", "ARS",
				new BigDecimal("0.175"), ASOF, FETCHED, Confidence.AGGREGATOR_AUTOMATIC, List.of())), ASOF);

		RankingQuery all = new RankingQuery(Money.ars("1000000"), 30, Set.of(),
				Set.of(RankedKind.PLAZO_FIJO, RankedKind.WALLET, RankedKind.CRYPTO), ASOF, 50, true);
		RankingResult grouped = service.rank(all);
		RankingResult flat = service.rank(new RankingQuery(all.amount(), 30, Set.of(), all.kinds(), ASOF, 50, false));

		assertThat(grouped.ranked()).hasSize(4).isEqualTo(flat.ranked());
		assertThat(grouped.ranked()).allSatisfy(entry -> assertThat(entry.foldedClasses()).isZero());
		assertThat(grouped.totals()).isEqualTo(flat.totals());
	}

	private static RankingQuery query(boolean groupFunds, int limit, RankedKind... kinds) {
		return new RankingQuery(Money.ars("1000000"), 30, Set.of(), Set.of(kinds), ASOF, limit, groupFunds);
	}

	private void seedFund(String name, String latestVcp) {
		seedFund(name, ASOF, latestVcp);
	}

	private void seedFund(String name, LocalDate latest, String latestVcp) {
		write(name, latest.minusDays(1), "1000");
		write(name, latest, latestVcp);
	}

	private void write(String name, LocalDate date, String vcp) {
		int separator = name.indexOf(" - ");
		FundQuote quote = new FundQuote(ArgentinaDatosFundAdapter.SOURCE, name,
				separator < 0 ? name : name.substring(0, separator),
				separator < 0 ? null : name.substring(separator + 3), "corto", Segment.SAVER, date,
				new BigDecimal(vcp), null, null, FETCHED, Confidence.AGGREGATOR_AUTOMATIC, List.of());
		fundWriter.write(List.of(quote), date);
	}

	private void seedBank(String name, String tna) {
		bankWriter.write(List.of(new BankPlazoFijoRate(ArgentinaDatosPlazoFijoAdapter.SOURCE, name, null,
				new BigDecimal(tna), List.of(), ASOF, FETCHED, Confidence.AGGREGATOR_AUTOMATIC, List.of())), ASOF);
	}

	private long instrumentId(InstrumentKind kind, String name) {
		return reader.instrument(kind, ArgentinaDatosFundAdapter.SOURCE.id(), name).orElseThrow().id();
	}
}
