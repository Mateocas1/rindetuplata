package ar.rindetuplata.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ar.rindetuplata.domain.Ineligible;
import ar.rindetuplata.domain.Money;
import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.Segment;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.ValidationFlag;
import ar.rindetuplata.source.crypto.ArgentinaDatosCryptoAdapter;
import ar.rindetuplata.source.crypto.CryptoYield;
import ar.rindetuplata.source.fund.ArgentinaDatosFundAdapter;
import ar.rindetuplata.source.fund.FundQuote;
import ar.rindetuplata.source.macro.BcraMonetariasClient;
import ar.rindetuplata.source.macro.MacroObservation;
import ar.rindetuplata.source.macro.MacroVariable;
import ar.rindetuplata.source.plazofijo.ArgentinaDatosPlazoFijoAdapter;
import ar.rindetuplata.source.plazofijo.BankPlazoFijoRate;
import ar.rindetuplata.source.plazofijo.TermRate;
import ar.rindetuplata.source.wallet.CuratedWalletRateAdapter;
import ar.rindetuplata.source.wallet.UalaWalletAdapter;
import ar.rindetuplata.source.wallet.WalletCondition;
import ar.rindetuplata.source.wallet.WalletRate;
import ar.rindetuplata.source.wallet.WalletTier;
import ar.rindetuplata.store.BankPlazoFijoWriter;
import ar.rindetuplata.store.CryptoYieldWriter;
import ar.rindetuplata.store.FundQuoteWriter;
import ar.rindetuplata.store.InstrumentKind;
import ar.rindetuplata.store.MacroObservationWriter;
import ar.rindetuplata.store.StoreReader;
import ar.rindetuplata.store.StoreTestConfiguration;
import ar.rindetuplata.store.WalletRateWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * T5a ranking rules against a real Postgres, seeded through the T4 store writers. Every rule asserts the rate that
 * was used and its kind, the final amount, the reason of anything excluded, the totals and the dates of each entry.
 */
@Import(StoreTestConfiguration.class)
@SpringBootTest
@Transactional
class RankingServiceTest {

	static final Instant FETCHED = Instant.parse("2026-10-14T19:06:23Z");
	static final LocalDate ASOF = LocalDate.parse("2026-10-14");

	@Autowired
	BankPlazoFijoWriter bankWriter;

	@Autowired
	WalletRateWriter walletWriter;

	@Autowired
	CryptoYieldWriter cryptoWriter;

	@Autowired
	FundQuoteWriter fundWriter;

	@Autowired
	MacroObservationWriter macroWriter;

	@Autowired
	RankingService service;

	@Autowired
	StoreReader reader;

	@Test
	void bankPlazoFijoUsesTheNonClientRateByDefault() {
		long id = seedBank("Banco Test", "0.21", "0.25",
				List.of(new TermRate(30, 60, null, null, decimal("0.30"))), ASOF, List.of());

		RankingResult result = service.rank(query(RankedKind.PLAZO_FIJO));

		assertThat(result.query()).isEqualTo(query(RankedKind.PLAZO_FIJO));
		assertThat(result.ranked()).hasSize(1);
		RankedEntry entry = result.ranked().get(0);
		assertThat(entry.id()).isEqualTo(String.valueOf(id));
		assertThat(entry.kind()).isEqualTo(RankedKind.PLAZO_FIJO);
		assertThat(entry.name()).isEqualTo("Banco Test");
		assertThat(entry.entity()).isEqualTo("Banco Test");
		assertThat(entry.source()).isEqualTo(new SourceView("argentinadatos-plazo-fijo", "ArgentinaDatos", false));
		assertThat(entry.confidence()).isEqualTo(Confidence.AGGREGATOR_AUTOMATIC);
		assertThat(entry.verifiedAt()).isNull();
		assertThat(entry.captureDate()).isEqualTo(ASOF);
		assertThat(entry.dataDate()).isEqualTo(ASOF);
		assertThat(entry.rateUsed().kind()).isEqualTo(RateKind.NON_CLIENT);
		assertThat(entry.rateUsed().basis()).isEqualTo("TNA");
		assertThat(entry.rateUsed().fraction()).isEqualByComparingTo("0.21");
		assertThat(entry.finalAmount().presented()).isEqualByComparingTo("1017260.27");
		assertThat(entry.earned().presented()).isEqualByComparingTo("17260.27");
		assertThat(entry.nominalReturn().setScale(12, RoundingMode.HALF_EVEN))
				.isEqualByComparingTo("0.017260273973");
		assertThat(entry.upTo()).isNull();
		assertThat(entry.warnings()).isEmpty();
		assertThat(result.ineligible()).isEmpty();
		assertThat(result.excluded()).isEmpty();
		assertThat(result.totals()).isEqualTo(new RankingTotals(1, 0, 0));
	}

	@Test
	void clientOfUsesTheClientRateAndTheMatchingTermBand() {
		long id = seedBank("Banco Test", "0.21", "0.25",
				List.of(new TermRate(30, 60, null, null, decimal("0.30"))), ASOF, List.of());

		RankingResult banded = service.rank(new RankingQuery(Money.ars("1000000"), 30, Set.of(id),
				Set.of(RankedKind.PLAZO_FIJO), ASOF, 50));
		RankedEntry band = banded.ranked().get(0);
		assertThat(band.rateUsed().kind()).isEqualTo(RateKind.CLIENT_TERM_BAND);
		assertThat(band.rateUsed().fraction()).isEqualByComparingTo("0.30");
		assertThat(band.finalAmount().presented()).isEqualByComparingTo("1024657.53");

		RankingResult base = service.rank(new RankingQuery(Money.ars("1000000"), 90, Set.of(id),
				Set.of(RankedKind.PLAZO_FIJO), ASOF, 50));
		RankedEntry baseEntry = base.ranked().get(0);
		assertThat(baseEntry.rateUsed().kind()).isEqualTo(RateKind.CLIENT);
		assertThat(baseEntry.rateUsed().fraction()).isEqualByComparingTo("0.25");
		assertThat(baseEntry.finalAmount().presented()).isEqualByComparingTo("1061643.84");
	}

	@Test
	void bankNotOfferedToNonClientsIsExcludedButRankedForClients() {
		long id = seedBank("Banco Solo Clientes", null, "0.25", List.of(), ASOF, List.of());

		RankingResult nonClient = service.rank(query(RankedKind.PLAZO_FIJO));
		assertThat(nonClient.ranked()).isEmpty();
		assertThat(nonClient.excluded()).hasSize(1);
		ExcludedEntry excluded = nonClient.excluded().get(0);
		assertThat(excluded.id()).isEqualTo(String.valueOf(id));
		assertThat(excluded.kind()).isEqualTo(RankedKind.PLAZO_FIJO);
		assertThat(excluded.name()).isEqualTo("Banco Solo Clientes");
		assertThat(excluded.reason()).isEqualTo(ExcludedReason.NOT_OFFERED_TO_NON_CLIENTS);
		assertThat(nonClient.totals()).isEqualTo(new RankingTotals(0, 0, 1));

		RankingResult client = service.rank(new RankingQuery(Money.ars("1000000"), 30, Set.of(id),
				Set.of(RankedKind.PLAZO_FIJO), ASOF, 50));
		assertThat(client.ranked()).hasSize(1);
		assertThat(client.ranked().get(0).rateUsed().kind()).isEqualTo(RateKind.CLIENT);
		assertThat(client.ranked().get(0).rateUsed().fraction()).isEqualByComparingTo("0.25");
	}

	@Test
	void bankWithoutAClientRateIsExcludedForClients() {
		long id = seedBank("Banco Sin Clientes", "0.22", null, List.of(), ASOF, List.of());

		RankingResult client = service.rank(new RankingQuery(Money.ars("1000000"), 30, Set.of(id),
				Set.of(RankedKind.PLAZO_FIJO), ASOF, 50));

		assertThat(client.ranked()).isEmpty();
		assertThat(client.excluded()).hasSize(1);
		assertThat(client.excluded().get(0).id()).isEqualTo(String.valueOf(id));
		assertThat(client.excluded().get(0).reason()).isEqualTo(ExcludedReason.NO_CLIENT_RATE);
	}

	@Test
	void depositsBelowTheMinimumTermLandInIneligible() {
		long id = seedBank("Banco 30 Dias", "0.21", null, List.of(), ASOF, List.of());

		RankingResult result = service.rank(new RankingQuery(Money.ars("1000000"), 29, Set.of(),
				Set.of(RankedKind.PLAZO_FIJO), ASOF, 50));

		assertThat(result.ranked()).isEmpty();
		assertThat(result.ineligible()).hasSize(1);
		IneligibleEntry entry = result.ineligible().get(0);
		assertThat(entry.id()).isEqualTo(String.valueOf(id));
		assertThat(entry.kind()).isEqualTo(RankedKind.PLAZO_FIJO);
		assertThat(entry.name()).isEqualTo("Banco 30 Dias");
		assertThat(entry.ineligible().reason()).isEqualTo(Ineligible.Reason.MINIMUM_TERM);
		assertThat(entry.ineligible().detail()).isEqualTo("Minimum term is 30 days, requested 29");
		assertThat(result.totals()).isEqualTo(new RankingTotals(0, 1, 0));
	}

	@Test
	void walletTiersBecomeBracketsAndConditionsBecomeUpTo() {
		long id = seedWallet(UalaWalletAdapter.SOURCE, "uala", "Ualá",
				List.of(new WalletTier(decimal("10000"), decimal("1000000"), decimal("0.19"))),
				List.of(new WalletCondition("Si acumulás $50.000 en ciertas operaciones este mes",
						List.of(new WalletTier(null, null, decimal("0.20"))))),
				null, Confidence.SCRAPED_ENTITY_WEB, ASOF, ASOF, List.of());

		RankingResult result = service.rank(query(RankedKind.WALLET));

		assertThat(result.ranked()).hasSize(1);
		RankedEntry entry = result.ranked().get(0);
		assertThat(entry.id()).isEqualTo(String.valueOf(id));
		assertThat(entry.kind()).isEqualTo(RankedKind.WALLET);
		assertThat(entry.name()).isEqualTo("Ualá");
		assertThat(entry.entity()).isNull();
		assertThat(entry.source()).isEqualTo(new SourceView("uala-cuenta-remunerada", "Ualá", false));
		assertThat(entry.confidence()).isEqualTo(Confidence.SCRAPED_ENTITY_WEB);
		assertThat(entry.verifiedAt()).isNull();
		assertThat(entry.captureDate()).isEqualTo(ASOF);
		assertThat(entry.dataDate()).isEqualTo(ASOF);
		assertThat(entry.rateUsed().kind()).isEqualTo(RateKind.WALLET_BASE);
		assertThat(entry.rateUsed().fraction()).isEqualByComparingTo("0.19");
		assertThat(entry.finalAmount().presented()).isEqualByComparingTo("1015460.27");
		assertThat(entry.earned().presented()).isEqualByComparingTo("15460.27");
		assertThat(entry.nominalReturn().setScale(12, RoundingMode.HALF_EVEN))
				.isEqualByComparingTo("0.015460273973");
		assertThat(entry.upTo()).isNotNull();
		assertThat(entry.upTo().condition()).isEqualTo("Si acumulás $50.000 en ciertas operaciones este mes");
		assertThat(entry.upTo().finalAmount().presented()).isEqualByComparingTo("1016569.63");
	}

	@Test
	void curatedWalletKeepsItsVerifiedAtAndCapBracket() {
		LocalDate verified = LocalDate.parse("2026-10-13");
		seedWallet(CuratedWalletRateAdapter.SOURCE, "naranja-x", "Naranja X",
				List.of(new WalletTier(null, decimal("1000000"), decimal("0.20"))), List.of(), verified,
				Confidence.CURATED_MANUAL, verified, verified, List.of());

		RankingResult result = service.rank(query(RankedKind.WALLET));

		RankedEntry entry = result.ranked().get(0);
		assertThat(entry.source()).isEqualTo(new SourceView("curated-wallet-rates", "Curated by the project owner", false));
		assertThat(entry.confidence()).isEqualTo(Confidence.CURATED_MANUAL);
		assertThat(entry.verifiedAt()).isEqualTo(verified);
		assertThat(entry.captureDate()).isEqualTo(verified);
		assertThat(entry.dataDate()).isEqualTo(verified);
		assertThat(entry.rateUsed().fraction()).isEqualByComparingTo("0.20");
		assertThat(entry.finalAmount().presented()).isEqualByComparingTo("1016438.36");
		assertThat(entry.earned().presented()).isEqualByComparingTo("16438.36");
	}

	@Test
	void fciBackedWalletIsRankedAtItsClassYield() {
		seedFund("Mercado Fondo - Clase A", LocalDate.parse("2026-10-13"), "1000");
		seedFund("Mercado Fondo - Clase A", ASOF, "1000.5");

		RankingResult result = service.rank(query(RankedKind.WALLET));

		assertThat(result.ranked()).hasSize(1);
		RankedEntry entry = result.ranked().get(0);
		assertThat(entry.kind()).isEqualTo(RankedKind.WALLET);
		assertThat(entry.name()).isEqualTo("Mercado Pago");
		assertThat(entry.entity()).isEqualTo("Mercado Fondo - Clase A");
		assertThat(entry.source())
				.isEqualTo(new SourceView("argentinadatos-fci-mercado-dinero", "ArgentinaDatos", false));
		assertThat(entry.confidence()).isEqualTo(Confidence.AGGREGATOR_AUTOMATIC);
		assertThat(entry.captureDate()).isEqualTo(ASOF);
		assertThat(entry.dataDate()).isEqualTo(ASOF);
		assertThat(entry.rateUsed().kind()).isEqualTo(RateKind.FUND_TNA_EQUIVALENT);
		assertThat(entry.rateUsed().fraction()).isEqualByComparingTo("0.1825");
		assertThat(entry.finalAmount().presented()).isEqualByComparingTo("1015109.26");
	}

	@Test
	void fciBackedWalletIsShownOnceWhenWalletsAndFundsAreRanked() {
		seedFund("Mercado Fondo - Clase A", LocalDate.parse("2026-10-13"), "1000");
		seedFund("Mercado Fondo - Clase A", ASOF, "1000.5");
		seedFund("Fondo Test - Clase A", LocalDate.parse("2026-10-13"), "1000");
		seedFund("Fondo Test - Clase A", ASOF, "1000.4");

		RankingResult result = service.rank(query(RankedKind.WALLET, RankedKind.FUND));

		assertThat(result.ranked()).extracting(RankedEntry::kind, RankedEntry::name).containsExactly(
				tuple(RankedKind.WALLET, "Mercado Pago"), tuple(RankedKind.FUND, "Fondo Test - Clase A"));
		assertThat(result.ranked().get(0).entity()).isEqualTo("Mercado Fondo - Clase A");
		assertThat(result.excluded()).isEmpty();
		assertThat(result.totals()).isEqualTo(new RankingTotals(2, 0, 0));
	}

	@Test
	void fciBackedFundClassIsListedWhenOnlyFundsAreRanked() {
		seedFund("Mercado Fondo - Clase A", LocalDate.parse("2026-10-13"), "1000");
		seedFund("Mercado Fondo - Clase A", ASOF, "1000.5");

		RankingResult result = service.rank(query(RankedKind.FUND));

		assertThat(result.ranked()).extracting(RankedEntry::kind, RankedEntry::name)
				.containsExactly(tuple(RankedKind.FUND, "Mercado Fondo - Clase A"));
		assertThat(result.totals()).isEqualTo(new RankingTotals(1, 0, 0));
	}

	@Test
	void fciBackedFundClassIsNotExcludedTwiceWhenItsHistoryIsShort() {
		seedFund("Mercado Fondo - Clase A", ASOF, "1000.5");

		RankingResult result = service.rank(query(RankedKind.WALLET, RankedKind.FUND));

		assertThat(result.ranked()).isEmpty();
		assertThat(result.excluded()).extracting(ExcludedEntry::kind, ExcludedEntry::name, ExcludedEntry::reason)
				.containsExactly(tuple(RankedKind.WALLET, "Mercado Pago", ExcludedReason.INSUFFICIENT_HISTORY));
		assertThat(result.totals()).isEqualTo(new RankingTotals(0, 0, 1));
	}

	@Test
	void fundClassUsesTheYieldOfItsTwoLatestQuotes() {
		seedFund("Fondo Test - Clase A", LocalDate.parse("2026-10-08"), "900");
		seedFund("Fondo Test - Clase A", LocalDate.parse("2026-10-13"), "1000");
		seedFund("Fondo Test - Clase A", ASOF, "1000.5");

		RankingResult result = service.rank(query(RankedKind.FUND));

		assertThat(result.ranked()).hasSize(1);
		RankedEntry entry = result.ranked().get(0);
		assertThat(entry.kind()).isEqualTo(RankedKind.FUND);
		assertThat(entry.name()).isEqualTo("Fondo Test - Clase A");
		assertThat(entry.entity()).isEqualTo("Fondo Test");
		assertThat(entry.source())
				.isEqualTo(new SourceView("argentinadatos-fci-mercado-dinero", "ArgentinaDatos", false));
		assertThat(entry.confidence()).isEqualTo(Confidence.AGGREGATOR_AUTOMATIC);
		assertThat(entry.captureDate()).isEqualTo(ASOF);
		assertThat(entry.dataDate()).isEqualTo(ASOF);
		assertThat(entry.rateUsed().kind()).isEqualTo(RateKind.FUND_TNA_EQUIVALENT);
		assertThat(entry.rateUsed().fraction()).isEqualByComparingTo("0.1825");
		assertThat(entry.finalAmount().presented()).isEqualByComparingTo("1015109.26");
		assertThat(entry.earned().presented()).isEqualByComparingTo("15109.26");
		assertThat(entry.nominalReturn().setScale(12, RoundingMode.HALF_EVEN))
				.isEqualByComparingTo("0.015109259217");
		assertThat(result.totals()).isEqualTo(new RankingTotals(1, 0, 0));
	}

	@Test
	void fundClassWithOneQuoteHasInsufficientHistory() {
		seedFund("Fondo Nuevo - Clase A", ASOF, "1000");

		RankingResult result = service.rank(query(RankedKind.FUND));

		assertThat(result.ranked()).isEmpty();
		assertThat(result.excluded()).extracting(ExcludedEntry::reason)
				.containsExactly(ExcludedReason.INSUFFICIENT_HISTORY);
	}

	@Test
	void fundClassWithOnlyStaleQuotesIsExcluded() {
		seedFund("Fondo Viejo - Clase A", LocalDate.parse("2026-09-30"), "1000");
		seedFund("Fondo Viejo - Clase A", LocalDate.parse("2026-10-01"), "1001");

		RankingResult result = service.rank(query(RankedKind.FUND));

		assertThat(result.ranked()).isEmpty();
		assertThat(result.excluded()).extracting(ExcludedEntry::reason).containsExactly(ExcludedReason.STALE);
		assertThat(result.excluded().get(0).captureDate()).isEqualTo(LocalDate.parse("2026-10-01"));
	}

	@Test
	void fundClassExactlySevenDaysOldIsStillRanked() {
		seedFund("Fondo Justo - Clase A", LocalDate.parse("2026-10-06"), "1000");
		seedFund("Fondo Justo - Clase A", LocalDate.parse("2026-10-07"), "1000.5");

		RankingResult result = service.rank(query(RankedKind.FUND));

		assertThat(result.ranked()).hasSize(1);
		assertThat(result.excluded()).isEmpty();
		assertThat(result.ranked().get(0).captureDate()).isEqualTo(LocalDate.parse("2026-10-07"));
	}

	@Test
	void fundClassWithFallingVcpIsExcludedWithNegativeReturn() {
		seedFund("Fondo Cayendo - Clase A", LocalDate.parse("2026-10-13"), "1000");
		seedFund("Fondo Cayendo - Clase A", ASOF, "999.5");

		RankingResult result = service.rank(query(RankedKind.FUND));

		assertThat(result.ranked()).isEmpty();
		assertThat(result.excluded()).extracting(ExcludedEntry::reason).containsExactly(ExcludedReason.NEGATIVE_RETURN);
	}

	@Test
	void arsCryptoApyBecomesTnaAndForeignCurrencyIsIgnored() {
		long arsId = seedCrypto("belo", "ARS", "0.175", ASOF, List.of());
		seedCrypto("nexo", "BTC", "0.062", ASOF, List.of());

		RankingResult result = service.rank(query(RankedKind.CRYPTO));

		assertThat(result.ranked()).hasSize(1);
		RankedEntry entry = result.ranked().get(0);
		assertThat(entry.id()).isEqualTo(String.valueOf(arsId));
		assertThat(entry.kind()).isEqualTo(RankedKind.CRYPTO);
		assertThat(entry.name()).isEqualTo("belo ARS");
		assertThat(entry.entity()).isEqualTo("belo");
		assertThat(entry.source()).isEqualTo(new SourceView("argentinadatos-rendimientos", "ArgentinaDatos", false));
		assertThat(entry.rateUsed().kind()).isEqualTo(RateKind.APY_TNA_EQUIVALENT);
		assertThat(entry.rateUsed().fraction().setScale(12, RoundingMode.HALF_EVEN))
				.isEqualByComparingTo("0.161303779440");
		assertThat(entry.finalAmount().presented()).isEqualByComparingTo("1013343.15");
		assertThat(entry.nominalReturn().setScale(12, RoundingMode.HALF_EVEN))
				.isEqualByComparingTo("0.013343152065");
		assertThat(result.excluded()).isEmpty();
		assertThat(result.totals()).isEqualTo(new RankingTotals(1, 0, 0));
	}

	@Test
	void invalidRowsGoToExcludedWithTheirFlagsAndWarningsTravelWithTheEntry() {
		ValidationFlag invalid = new ValidationFlag(FlagCode.RATE_ABOVE_MAXIMUM, Severity.INVALID, "rate above 300%");
		ValidationFlag warning = new ValidationFlag(FlagCode.STALE, Severity.WARNING, "data date is older than 45 days");
		long invalidBank = seedBank("Banco Invalido", "0.21", null, List.of(), ASOF, List.of(invalid));
		long validBank = seedBank("Banco Valido", "0.21", null, List.of(), ASOF, List.of(warning));
		long invalidCrypto = seedCrypto("ripio", "ARS", "0.5", ASOF, List.of(invalid));

		RankingResult result = service.rank(query(RankedKind.PLAZO_FIJO, RankedKind.CRYPTO));

		assertThat(result.ranked()).extracting(RankedEntry::id).containsExactly(String.valueOf(validBank));
		assertThat(result.ranked().get(0).warnings()).containsExactly(warning);
		assertThat(result.excluded()).extracting(ExcludedEntry::id, ExcludedEntry::reason)
				.containsExactlyInAnyOrder(
						tuple(String.valueOf(invalidBank), ExcludedReason.INVALID_DATA),
						tuple(String.valueOf(invalidCrypto), ExcludedReason.INVALID_DATA));
		assertThat(result.excluded()).allSatisfy(entry -> {
			assertThat(entry.captureDate()).isEqualTo(ASOF);
			assertThat(entry.flags()).contains(invalid);
		});
		assertThat(result.totals()).isEqualTo(new RankingTotals(1, 0, 2));
	}

	@Test
	void eachKindKeepsItsOwnLatestCaptureDate() {
		long bankId = seedBank("Banco Fechado", "0.21", null, List.of(), ASOF, List.of());
		long ualaId = seedWallet(UalaWalletAdapter.SOURCE, "uala", "Ualá",
				List.of(new WalletTier(decimal("0"), null, decimal("0.19"))), List.of(), null,
				Confidence.SCRAPED_ENTITY_WEB, ASOF, ASOF, List.of());
		LocalDate curatedDate = LocalDate.parse("2026-10-13");
		long curatedId = seedWallet(CuratedWalletRateAdapter.SOURCE, "naranja-x", "Naranja X",
				List.of(new WalletTier(null, decimal("1000000"), decimal("0.20"))), List.of(), curatedDate,
				Confidence.CURATED_MANUAL, curatedDate, curatedDate, List.of());
		long cryptoId = seedCrypto("belo", "ARS", "0.175", LocalDate.parse("2026-10-12"), List.of());

		RankingResult result = service.rank(new RankingQuery(Money.ars("1000000"), 30, Set.of(),
				Set.of(RankedKind.PLAZO_FIJO, RankedKind.WALLET, RankedKind.CRYPTO), ASOF, 50));

		Map<String, RankedEntry> byId = result.ranked().stream()
				.collect(Collectors.toMap(RankedEntry::id, Function.identity()));
		assertThat(byId).hasSize(4);
		assertThat(byId.get(String.valueOf(bankId)).captureDate()).isEqualTo(ASOF);
		assertThat(byId.get(String.valueOf(bankId)).dataDate()).isEqualTo(ASOF);
		assertThat(byId.get(String.valueOf(ualaId)).captureDate()).isEqualTo(ASOF);
		assertThat(byId.get(String.valueOf(curatedId)).captureDate()).isEqualTo(curatedDate);
		assertThat(byId.get(String.valueOf(curatedId)).dataDate()).isEqualTo(curatedDate);
		assertThat(byId.get(String.valueOf(cryptoId)).captureDate()).isEqualTo(LocalDate.parse("2026-10-12"));
		assertThat(byId.get(String.valueOf(cryptoId)).dataDate()).isEqualTo(LocalDate.parse("2026-10-12"));
	}

	@Test
	void remInflationIsCompoundedOverTheHorizonAndDrivesTheRealReturn() {
		seedRem("0.20", LocalDate.parse("2026-09-30"));
		seedRem("0.211", LocalDate.parse("2026-10-09"));
		seedRem("0.30", LocalDate.parse("2026-10-20"));
		seedBank("Banco REM", "0.21", null, List.of(), ASOF, List.of());

		RankingResult result = service.rank(query(RankedKind.PLAZO_FIJO));

		assertThat(result.inflation()).isNotNull();
		assertThat(result.inflation().index()).isEqualTo("REM_12M_EXPECTED");
		assertThat(result.inflation().twelveMonthRate()).isEqualByComparingTo("0.211");
		assertThat(result.inflation().observationDate()).isEqualTo(LocalDate.parse("2026-10-09"));
		assertThat(result.inflation().overHorizon().setScale(12, RoundingMode.HALF_EVEN))
				.isEqualByComparingTo("0.015859778003");
		RankedEntry entry = result.ranked().get(0);
		assertThat(entry.nominalReturn().setScale(12, RoundingMode.HALF_EVEN))
				.isEqualByComparingTo("0.017260273973");
		assertThat(entry.realReturn().setScale(12, RoundingMode.HALF_EVEN))
				.isEqualByComparingTo("0.001378631185");
	}

	@Test
	void withoutARemObservationTheRankingIsReturnedWithNullRealReturn() {
		seedRem("0.211", LocalDate.parse("2026-10-20"));
		seedBank("Banco Sin REM", "0.21", null, List.of(), ASOF, List.of());

		RankingResult result = service.rank(query(RankedKind.PLAZO_FIJO));

		assertThat(result.inflation()).isNull();
		assertThat(result.ranked()).hasSize(1);
		assertThat(result.ranked().get(0).realReturn()).isNull();
	}

	@Test
	void limitTrimsTheRankedListButTotalsCountEveryCandidate() {
		seedBank("Banco A", "0.30", null, List.of(), ASOF, List.of());
		seedBank("Banco B", "0.20", null, List.of(), ASOF, List.of());
		seedBank("Banco C", "0.10", null, List.of(), ASOF, List.of());

		RankingResult result = service.rank(new RankingQuery(Money.ars("1000000"), 30, Set.of(),
				Set.of(RankedKind.PLAZO_FIJO), ASOF, 2));

		assertThat(result.ranked()).extracting(RankedEntry::name).containsExactly("Banco A", "Banco B");
		assertThat(result.totals()).isEqualTo(new RankingTotals(3, 0, 0));
	}

	@Test
	void onlyTheRequestedKindsAreRead() {
		seedBank("Banco A", "0.30", null, List.of(), ASOF, List.of());
		seedWallet(UalaWalletAdapter.SOURCE, "uala", "Ualá",
				List.of(new WalletTier(null, null, decimal("0.19"))), List.of(), null,
				Confidence.SCRAPED_ENTITY_WEB, ASOF, ASOF, List.of());
		seedFund("Mercado Fondo - Clase A", LocalDate.parse("2026-10-13"), "1000");
		seedFund("Mercado Fondo - Clase A", ASOF, "1000.5");

		RankingResult result = service.rank(query(RankedKind.WALLET));

		assertThat(result.ranked()).extracting(RankedEntry::kind).containsOnly(RankedKind.WALLET);
		assertThat(result.ranked()).extracting(RankedEntry::name).containsExactlyInAnyOrder("Ualá", "Mercado Pago");
		assertThat(result.ineligible()).isEmpty();
		assertThat(result.excluded()).isEmpty();
		assertThat(result.totals()).isEqualTo(new RankingTotals(2, 0, 0));
	}

	@Test
	void simulateReturnsTheSameEntryAsTheRanking() {
		long id = seedBank("Banco Simulable", "0.21", "0.25",
				List.of(new TermRate(30, 60, null, null, decimal("0.30"))), ASOF, List.of());
		RankedEntry ranked = service.rank(new RankingQuery(Money.ars("1000000"), 30, Set.of(),
				Set.of(RankedKind.PLAZO_FIJO), ASOF, 50)).ranked().get(0);

		SimulationResult result = service.simulate(id, Money.ars("1000000"), 30, false);

		assertThat(result).isInstanceOf(SimulationResult.Simulated.class);
		RankedEntry simulated = ((SimulationResult.Simulated) result).entry();
		assertThat(simulated.id()).isEqualTo(ranked.id());
		assertThat(simulated.rateUsed().kind()).isEqualTo(ranked.rateUsed().kind());
		assertThat(simulated.rateUsed().fraction()).isEqualByComparingTo(ranked.rateUsed().fraction());
		assertThat(simulated.finalAmount().presented()).isEqualByComparingTo("1017260.27");

		SimulationResult client = service.simulate(id, Money.ars("1000000"), 30, true);

		RankedEntry clientEntry = ((SimulationResult.Simulated) client).entry();
		assertThat(clientEntry.rateUsed().kind()).isEqualTo(RateKind.CLIENT_TERM_BAND);
		assertThat(clientEntry.rateUsed().fraction()).isEqualByComparingTo("0.30");
		assertThat(clientEntry.finalAmount().presented()).isEqualByComparingTo("1024657.53");
	}

	@Test
	void simulateExplainsIneligibility() {
		long id = seedBank("Banco 30 Dias", "0.21", null, List.of(), ASOF, List.of());

		SimulationResult result = service.simulate(id, Money.ars("1000000"), 29, false);

		assertThat(result).isInstanceOf(SimulationResult.IneligibleInstrument.class);
		Ineligible ineligible = ((SimulationResult.IneligibleInstrument) result).ineligible();
		assertThat(ineligible.reason()).isEqualTo(Ineligible.Reason.MINIMUM_TERM);
		assertThat(ineligible.detail()).isEqualTo("Minimum term is 30 days, requested 29");
	}

	@Test
	void simulateExplainsExclusion() {
		long id = seedBank("Banco Solo Clientes", null, "0.25", List.of(), ASOF, List.of());

		SimulationResult result = service.simulate(id, Money.ars("1000000"), 30, false);

		assertThat(result).isInstanceOf(SimulationResult.ExcludedInstrument.class);
		assertThat(((SimulationResult.ExcludedInstrument) result).reason())
				.isEqualTo(ExcludedReason.NOT_OFFERED_TO_NON_CLIENTS);
	}

	@Test
	void simulateUnknownInstrumentIsTypedNotFound() {
		assertThatThrownBy(() -> service.simulate(999_999L, Money.ars("1000000"), 30, false))
				.isInstanceOf(InstrumentNotFoundException.class);
	}

	private static RankingQuery query(RankedKind... kinds) {
		return new RankingQuery(Money.ars("1000000"), 30, Set.of(), Set.of(kinds), ASOF, 50);
	}

	private long seedBank(String name, String nonClientTna, String clientTna, List<TermRate> terms,
			LocalDate captureDate, List<ValidationFlag> flags) {
		BankPlazoFijoRate rate = new BankPlazoFijoRate(ArgentinaDatosPlazoFijoAdapter.SOURCE, name,
				decimal(clientTna), decimal(nonClientTna), terms, captureDate, FETCHED,
				Confidence.AGGREGATOR_AUTOMATIC, flags);
		bankWriter.write(List.of(rate), captureDate);
		return instrumentId(InstrumentKind.PLAZO_FIJO_BANK, ArgentinaDatosPlazoFijoAdapter.SOURCE.id(), name);
	}

	private long seedWallet(SourceRef source, String walletId, String name, List<WalletTier> tiers,
			List<WalletCondition> conditions, LocalDate verifiedAt, Confidence confidence, LocalDate dataDate,
			LocalDate captureDate, List<ValidationFlag> flags) {
		WalletRate rate = new WalletRate(source, walletId, name, tiers, conditions, dataDate, verifiedAt, FETCHED,
				confidence, flags);
		walletWriter.write(List.of(rate), captureDate);
		return instrumentId(InstrumentKind.WALLET, source.id(), walletId);
	}

	private long seedCrypto(String platform, String currency, String apy, LocalDate date, List<ValidationFlag> flags) {
		CryptoYield yield = new CryptoYield(ArgentinaDatosCryptoAdapter.SOURCE, platform, currency, decimal(apy), date,
				FETCHED, Confidence.AGGREGATOR_AUTOMATIC, flags);
		cryptoWriter.write(List.of(yield), date);
		return instrumentId(InstrumentKind.CRYPTO_YIELD, ArgentinaDatosCryptoAdapter.SOURCE.id(),
				platform + "|" + currency);
	}

	private void seedFund(String name, LocalDate quoteDate, String vcp) {
		FundQuote quote = new FundQuote(ArgentinaDatosFundAdapter.SOURCE, name, fundOf(name), shareClassOf(name),
				"corto", Segment.SAVER, quoteDate, decimal(vcp), null, null, FETCHED,
				Confidence.AGGREGATOR_AUTOMATIC, List.of());
		fundWriter.write(List.of(quote), quoteDate);
	}

	private void seedRem(String value, LocalDate observationDate) {
		MacroObservation observation = new MacroObservation(BcraMonetariasClient.SOURCE,
				MacroVariable.EXPECTED_INFLATION_12M, observationDate, decimal(value), FETCHED,
				Confidence.OFFICIAL_AUTOMATIC, List.of());
		macroWriter.write(List.of(observation), observationDate);
	}

	private long instrumentId(InstrumentKind kind, String sourceId, String naturalKey) {
		return reader.instrument(kind, sourceId, naturalKey).orElseThrow().id();
	}

	private static String fundOf(String name) {
		int separator = name.indexOf(" - ");
		return separator < 0 ? name : name.substring(0, separator);
	}

	private static String shareClassOf(String name) {
		int separator = name.indexOf(" - ");
		return separator < 0 ? null : name.substring(separator + 3);
	}

	private static BigDecimal decimal(String value) {
		return value == null ? null : new BigDecimal(value);
	}
}
