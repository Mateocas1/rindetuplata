package ar.rindetuplata.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.source.crypto.ArgentinaDatosCryptoAdapter;
import ar.rindetuplata.source.dollar.DolarApiAdapter;
import ar.rindetuplata.source.fund.ArgentinaDatosFundAdapter;
import ar.rindetuplata.source.fund.FundQuote;
import ar.rindetuplata.source.macro.BcraMonetariasClient;
import ar.rindetuplata.source.macro.MacroVariable;
import ar.rindetuplata.source.plazofijo.ArgentinaDatosPlazoFijoAdapter;
import ar.rindetuplata.source.plazofijo.BankPlazoFijoRate;
import ar.rindetuplata.source.plazofijo.BcraPlazoFijoAdapter;
import ar.rindetuplata.source.plazofijo.OfficialPlazoFijoRate;
import ar.rindetuplata.source.wallet.CuratedWalletRateAdapter;
import ar.rindetuplata.source.wallet.UalaWalletAdapter;
import ar.rindetuplata.source.wallet.WalletRateSource;
import ar.rindetuplata.store.BankPlazoFijoWriter;
import ar.rindetuplata.store.CryptoYieldWriter;
import ar.rindetuplata.store.FundQuoteWriter;
import ar.rindetuplata.store.InstrumentKind;
import ar.rindetuplata.store.OfficialPlazoFijoWriter;
import ar.rindetuplata.store.RunMode;
import ar.rindetuplata.store.StoreReader;
import ar.rindetuplata.store.StoreReader.SourceRunRow;
import ar.rindetuplata.store.StoreTestConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * T4c live rules: a LIVE run writes every kind and records one {@code source_run} row per source; a failing port
 * never blocks the other kinds; re-running a COMPLETED date is a no-op; a FAILED instance restarts only its
 * unfinished steps; the scheduler ingests today in Buenos Aires; every wallet source bean is used.
 */
@Import({ StoreTestConfiguration.class, StubSourcesConfiguration.class, IngestionLiveTest.FixedClock.class })
@SpringBootTest(properties = { "rindetuplata.ingest.cron=0 0 0 1 1 *" })
class IngestionLiveTest {

	static final Instant FIXED_INSTANT = Instant.parse("2026-12-01T01:00:00Z");

	/** The same instant in UTC is 2026-12-01: only the Argentine zone makes "today" 2026-11-30. */
	static final LocalDate TODAY_IN_BUENOS_AIRES = LocalDate.parse("2026-11-30");

	static final LocalDate EVERY_KIND_DAY = LocalDate.parse("2026-11-01");
	static final LocalDate FAILING_PORT_DAY = LocalDate.parse("2026-11-02");
	static final LocalDate ALREADY_COMPLETED_DAY = LocalDate.parse("2026-11-03");
	static final LocalDate RESTART_DAY = LocalDate.parse("2026-11-04");
	static final LocalDate WALLET_DAY = LocalDate.parse("2026-11-05");

	static final String OFFICIAL = BcraPlazoFijoAdapter.SOURCE.id();
	static final String BANKS = ArgentinaDatosPlazoFijoAdapter.SOURCE.id();
	static final String FUNDS = ArgentinaDatosFundAdapter.SOURCE.id();
	static final String CRYPTO = ArgentinaDatosCryptoAdapter.SOURCE.id();
	static final String MACRO = BcraMonetariasClient.SOURCE.id();
	static final String DOLLAR = DolarApiAdapter.SOURCE.id();
	static final String UALA = UalaWalletAdapter.SOURCE.id();
	static final String CURATED = CuratedWalletRateAdapter.SOURCE.id();

	@Autowired
	IngestionService ingestionService;

	@Autowired
	IngestionScheduler scheduler;

	@Autowired
	StoreReader reader;

	@Autowired
	JobRepository jobRepository;

	@Autowired
	List<WalletRateSource> walletSources;

	@BeforeEach
	void resetStubs() {
		StubSources.reset();
	}

	@Test
	void liveRunWritesEveryKindAndASourceRunRowPerSource() {
		IngestionResult result = ingestionService.run(EVERY_KIND_DAY, RunMode.LIVE);

		assertThat(result.status()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(result.alreadyCompleted()).isFalse();
		assertThat(result.jobExecutionId()).isNotNull();

		List<OfficialPlazoFijoRate> official = IngestFixtures.officialRates();
		assertThat(reader.snapshot(InstrumentKind.PLAZO_FIJO_OFFICIAL, OFFICIAL,
				OfficialPlazoFijoWriter.naturalKey(official.get(0)), EVERY_KIND_DAY)).isPresent();

		List<BankPlazoFijoRate> banks = IngestFixtures.bankRates();
		assertThat(reader.snapshot(InstrumentKind.PLAZO_FIJO_BANK, BANKS,
				BankPlazoFijoWriter.naturalKey(banks.get(0)), EVERY_KIND_DAY)).isPresent();

		FundQuote fund = IngestFixtures.fundLatest().get(0);
		assertThat(reader.fundQuote(FUNDS, FundQuoteWriter.naturalKey(fund), fund.date())).isPresent();

		assertThat(reader.snapshot(InstrumentKind.CRYPTO_YIELD, CRYPTO,
				CryptoYieldWriter.naturalKey(IngestFixtures.cryptoYields().get(0)), EVERY_KIND_DAY)).isPresent();

		assertThat(reader.macro(MacroVariable.MONTHLY_INFLATION, LocalDate.parse("2026-09-30"), MACRO)).isPresent();

		assertThat(reader.dollar(DOLLAR, EVERY_KIND_DAY)).isPresent();

		assertThat(reader.sourceRunCount(EVERY_KIND_DAY)).isEqualTo(8);
		assertRowCount(EVERY_KIND_DAY, OFFICIAL,
				distinct(OfficialPlazoFijoWriter::naturalKey, official));
		assertRowCount(EVERY_KIND_DAY, BANKS, distinct(BankPlazoFijoWriter::naturalKey, banks));
		assertRowCount(EVERY_KIND_DAY, FUNDS,
				distinct(FundQuoteWriter::naturalKey, IngestFixtures.fundLatest())
						+ distinct(FundQuoteWriter::naturalKey, IngestFixtures.fundPrevious()));
		assertRowCount(EVERY_KIND_DAY, CRYPTO,
				distinct(CryptoYieldWriter::naturalKey, IngestFixtures.cryptoYields()));
		assertRowCount(EVERY_KIND_DAY, MACRO, IngestFixtures.macro(MacroVariable.MONTHLY_INFLATION).size()
				+ IngestFixtures.macro(MacroVariable.EXPECTED_INFLATION_12M).size()
				+ IngestFixtures.macro(MacroVariable.PSP_BENCHMARK_TNA).size());
		assertRowCount(EVERY_KIND_DAY, DOLLAR, 1);
		assertRowCount(EVERY_KIND_DAY, UALA, 1);
		assertRowCount(EVERY_KIND_DAY, CURATED, 1);
	}

	@Test
	void aFailingPortLeavesItsSourceRunFailedAndTheJobCompleted() {
		StubSources.CRYPTO_SOURCE_FAILURE = true;

		IngestionResult result = ingestionService.run(FAILING_PORT_DAY, RunMode.LIVE);

		assertThat(result.status()).isEqualTo(BatchStatus.COMPLETED);
		SourceRunRow crypto = runRow(FAILING_PORT_DAY, CRYPTO);
		assertThat(crypto.status()).isEqualTo("FAILED");
		assertThat(crypto.rows()).isZero();
		assertThat(crypto.failure()).contains("stub crypto source is down");

		assertThat(runRow(FAILING_PORT_DAY, OFFICIAL).status()).isEqualTo("OK");
		assertThat(runRow(FAILING_PORT_DAY, BANKS).status()).isEqualTo("OK");
		assertThat(runRow(FAILING_PORT_DAY, DOLLAR).status()).isEqualTo("OK");
		assertThat(reader.snapshot(InstrumentKind.PLAZO_FIJO_OFFICIAL, OFFICIAL,
				OfficialPlazoFijoWriter.naturalKey(IngestFixtures.officialRates().get(0)), FAILING_PORT_DAY)).isPresent();
		assertThat(reader.dollar(DOLLAR, FAILING_PORT_DAY)).isPresent();
	}

	@Test
	void aCompletedInstanceIsANoOpReportedAsAlreadyCompleted() {
		IngestionResult first = ingestionService.run(ALREADY_COMPLETED_DAY, RunMode.LIVE);
		assertThat(first.alreadyCompleted()).isFalse();
		int sourceRuns = reader.sourceRunCount(ALREADY_COMPLETED_DAY);
		int officialRows = runRow(ALREADY_COMPLETED_DAY, OFFICIAL).rows();
		int portfolioSnapshotRows = reader.snapshotCount(InstrumentKind.PLAZO_FIJO_OFFICIAL);
		JobInstance instance = jobRepository.getJobInstance(IngestionService.JOB_NAME,
				IngestionService.parameters(ALREADY_COMPLETED_DAY, RunMode.LIVE));

		IngestionResult second = ingestionService.run(ALREADY_COMPLETED_DAY, RunMode.LIVE);

		assertThat(second.alreadyCompleted()).isTrue();
		assertThat(second.status()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(second.failureMessages()).isEmpty();
		assertThat(StubSources.PLAZO_FIJO_CALLS).hasValue(1);
		assertThat(StubSources.FUND_CALLS).hasValue(1);
		assertThat(StubSources.CRYPTO_CALLS).hasValue(1);
		assertThat(StubSources.MACRO_CALLS).hasValue(3);
		assertThat(StubSources.DOLLAR_CALLS).hasValue(1);
		assertThat(StubSources.UALA_CALLS).hasValue(1);
		assertThat(StubSources.CURATED_CALLS).hasValue(1);
		assertThat(reader.sourceRunCount(ALREADY_COMPLETED_DAY)).isEqualTo(sourceRuns);
		assertThat(runRow(ALREADY_COMPLETED_DAY, OFFICIAL).rows()).isEqualTo(officialRows);
		assertThat(reader.snapshotCount(InstrumentKind.PLAZO_FIJO_OFFICIAL)).isEqualTo(portfolioSnapshotRows);
		assertThat(jobRepository.getJobExecutions(instance)).hasSize(1);
	}

	@Test
	void aFailedInstanceRestartsOnlyUnfinishedSteps() {
		StubSources.CRYPTO_FAILS_ONCE = true;

		IngestionResult failed = ingestionService.run(RESTART_DAY, RunMode.LIVE);

		assertThat(failed.status()).isEqualTo(BatchStatus.FAILED);
		assertThat(failed.alreadyCompleted()).isFalse();
		assertThat(failed.failureMessages()).anySatisfy(message -> assertThat(message).contains("exploded"));
		assertThat(StubSources.PLAZO_FIJO_CALLS).hasValue(1);
		assertThat(StubSources.FUND_CALLS).hasValue(1);
		assertThat(StubSources.CRYPTO_CALLS).hasValue(1);
		assertThat(StubSources.MACRO_CALLS).hasValue(0);
		assertThat(StubSources.DOLLAR_CALLS).hasValue(0);
		assertThat(StubSources.UALA_CALLS).hasValue(0);
		assertThat(StubSources.CURATED_CALLS).hasValue(0);

		IngestionResult restarted = ingestionService.run(RESTART_DAY, RunMode.LIVE);

		assertThat(restarted.status()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(StubSources.PLAZO_FIJO_CALLS).hasValue(1);
		assertThat(StubSources.FUND_CALLS).hasValue(1);
		assertThat(StubSources.CRYPTO_CALLS).hasValue(2);
		assertThat(StubSources.MACRO_CALLS).hasValue(3);
		assertThat(StubSources.DOLLAR_CALLS).hasValue(1);
		assertThat(StubSources.UALA_CALLS).hasValue(1);
		assertThat(StubSources.CURATED_CALLS).hasValue(1);
		assertThat(reader.sourceRunCount(RESTART_DAY)).isEqualTo(8);
	}

	@Test
	void theSchedulerLaunchesLiveForTodayInBuenosAires() {
		scheduler.runDailyIngestion();

		SourceRunRow dollar = runRow(TODAY_IN_BUENOS_AIRES, DOLLAR);
		assertThat(dollar.mode()).isEqualTo("LIVE");
		assertThat(dollar.status()).isEqualTo("OK");
		assertThat(reader.sourceRunCount(LocalDate.parse("2026-12-01"))).isZero();
	}

	@Test
	void everyWalletRateSourceBeanIsUsed() {
		assertThat(walletSources).hasSize(2);

		ingestionService.run(WALLET_DAY, RunMode.LIVE);

		assertThat(StubSources.UALA_CALLS).hasValue(1);
		assertThat(StubSources.CURATED_CALLS).hasValue(1);
		assertRowCount(WALLET_DAY, UALA, 1);
		assertRowCount(WALLET_DAY, CURATED, 1);
		assertThat(reader.snapshot(InstrumentKind.WALLET, UALA, "uala", WALLET_DAY)).isPresent();
		assertThat(reader.snapshot(InstrumentKind.WALLET, CURATED, "naranja-x", WALLET_DAY)).isPresent();
	}

	private SourceRunRow runRow(LocalDate date, String sourceId) {
		return reader.sourceRun(date, sourceId)
			.orElseThrow(() -> new AssertionError("no source_run row for " + sourceId + " on " + date));
	}

	private void assertRowCount(LocalDate date, String sourceId, int expectedRows) {
		SourceRunRow row = runRow(date, sourceId);
		assertThat(row.status()).as(sourceId).isEqualTo("OK");
		assertThat(row.mode()).as(sourceId).isEqualTo("LIVE");
		assertThat(row.rows()).as(sourceId).isEqualTo(expectedRows);
		assertThat(row.rows()).as(sourceId).isPositive();
	}

	private static <T> int distinct(Function<T, String> key, List<T> records) {
		return (int) records.stream().map(key).distinct().count();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClock {

		@Bean
		Clock clock() {
			return Clock.fixed(FIXED_INSTANT, ZoneId.of("America/Argentina/Buenos_Aires"));
		}
	}
}
