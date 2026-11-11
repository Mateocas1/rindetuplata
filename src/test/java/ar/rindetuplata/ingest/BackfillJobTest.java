package ar.rindetuplata.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;

import ar.rindetuplata.alert.AlertEvaluator;
import ar.rindetuplata.ranking.DataVersion;
import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.dollar.ArgentinaDatosDollarAdapter;
import ar.rindetuplata.source.dollar.DolarApiAdapter;
import ar.rindetuplata.source.dollar.DollarQuote;
import ar.rindetuplata.source.fund.ArgentinaDatosFundAdapter;
import ar.rindetuplata.source.fund.FundQuote;
import ar.rindetuplata.source.macro.BcraMonetariasClient;
import ar.rindetuplata.source.macro.MacroObservation;
import ar.rindetuplata.source.macro.MacroVariable;
import ar.rindetuplata.store.DollarQuoteWriter;
import ar.rindetuplata.store.FundQuoteWriter;
import ar.rindetuplata.store.MacroObservationWriter;
import ar.rindetuplata.store.RunMode;
import ar.rindetuplata.store.RunStatus;
import ar.rindetuplata.store.SourceRunWriter;
import ar.rindetuplata.store.StoreReader;
import ar.rindetuplata.store.StoreReader.SourceRunRow;
import ar.rindetuplata.store.StoreTestConfiguration;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * {@code historyBackfillJob(from, to)} over the stubbed history ports. It reads the dated FCI
 * endpoint once per day (waiting the configured polite delay between calls), the ArgentinaDatos MEP series once, and
 * BCRA macro by date range once per variable; every write is insert-only (live data wins); it records one
 * {@code source_run} row per source and day with mode BACKFILL; it has no alert or rebuild listener.
 */
@Import({ StoreTestConfiguration.class, StubSourcesConfiguration.class, BackfillJobTest.Instrumentation.class })
@SpringBootTest(properties = { "rindetuplata.ingest.enabled=false", "rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.ingest.rebuild-hook-url=" + BackfillJobTest.HOOK_URL })
class BackfillJobTest {

	static final String HOOK_URL = "http://rebuild-hook.test/deploy";

	static final LocalDate FROM = LocalDate.parse("2026-09-28");
	static final LocalDate TO = LocalDate.parse("2026-10-01");
	static final List<LocalDate> DAYS = List.of(FROM, LocalDate.parse("2026-09-29"), LocalDate.parse("2026-09-30"), TO);

	static final String FUNDS = ArgentinaDatosFundAdapter.SOURCE.id();
	static final String MEP = ArgentinaDatosDollarAdapter.SOURCE.id();
	static final String DOLAR_API = DolarApiAdapter.SOURCE.id();
	static final String MACRO = BcraMonetariasClient.SOURCE.id();
	static final String MERCADO_FONDO = "Mercado Fondo - Clase A";
	static final String SUMMARY = "history-backfill";

	private static final Instant LIVE_AT = Instant.parse("2026-09-29T22:30:00Z");

	@Autowired
	BackfillService backfill;

	@Autowired
	IngestionService ingestionService;

	@Autowired
	BackfillProperties properties;

	@Autowired
	StoreReader reader;

	@Autowired
	JobRepository jobRepository;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	DataVersion dataVersion;

	@Autowired
	FundQuoteWriter fundWriter;

	@Autowired
	DollarQuoteWriter dollarWriter;

	@Autowired
	MacroObservationWriter macroWriter;

	@Autowired
	SourceRunWriter runWriter;

	@BeforeEach
	void emptyDatabaseAndStubs() {
		StubSources.reset();
		Instrumentation.reset();
		jdbc.sql("truncate fund_quote, rate_snapshot, instrument, macro_observation, dollar_quote, source_run cascade")
			.update();
	}

	private JobExecution runBackfill(LocalDate from, LocalDate to) {
		BackfillStarted started = backfill.start(from, to);
		long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
		JobExecution execution = jobRepository.getJobExecution(started.jobId());
		assertThat(execution).as("the job execution %s exists", started.jobId()).isNotNull();
		while ((backfill.running() || execution.isRunning()) && System.nanoTime() < deadline) {
			sleep(20);
			execution = jobRepository.getJobExecution(started.jobId());
		}
		assertThat(backfill.running()).as("the backfill finished").isFalse();
		return jobRepository.getJobExecution(started.jobId());
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private SourceRunRow run(LocalDate day, String source) {
		return reader.sourceRun(day, source).orElseThrow(() -> new AssertionError("no source_run for " + source + " on " + day));
	}

	private static int fixtureRows() {
		return IngestFixtures.fundDay(FROM).size();
	}

	@Test
	void aRangeWritesEveryKindAndOneBackfillRunPerSourceAndDay() {
		String versionBefore = dataVersion.current();

		JobExecution execution = runBackfill(FROM, TO);

		assertThat(execution.getJobInstance().getJobName()).isEqualTo("historyBackfillJob");
		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(execution.getJobParameters().getString("from")).isEqualTo("2026-09-28");
		assertThat(execution.getJobParameters().getString("to")).isEqualTo("2026-10-01");

		// Funds: the dated endpoint once per day, in order.
		assertThat(StubSources.FUND_HISTORY_DAYS).containsExactlyElementsOf(DAYS);
		for (LocalDate day : DAYS) {
			assertThat(reader.fundQuote(FUNDS, MERCADO_FONDO, day)).as("fund quote of %s", day).isPresent();
		}
		assertThat(reader.fundQuoteCount()).isEqualTo(4 * fixtureRows());

		// MEP: the series once, only the days of the range, under the ArgentinaDatos source id.
		assertThat(StubSources.DOLLAR_SERIES_CALLS).hasValue(1);
		for (LocalDate day : DAYS) {
			assertThat(reader.dollar(MEP, day)).as("MEP of %s", day).isPresent();
		}
		assertThat(reader.dollar(MEP, LocalDate.parse("2026-10-02"))).as("outside the range").isEmpty();
		assertThat(reader.dollar(DOLAR_API, TO)).isEmpty();
		assertThat(reader.dollarCount()).isEqualTo(4);
		assertThat(reader.dollar(MEP, TO).orElseThrow().buy()).isEqualByComparingTo("1537.2");

		// Macro: one range call per variable with the run's range, observations inside it only.
		assertThat(StubSources.MACRO_RANGE_CALLS).containsExactlyInAnyOrder(
				"MONTHLY_INFLATION|2026-09-28|2026-10-01", "EXPECTED_INFLATION_12M|2026-09-28|2026-10-01",
				"PSP_BENCHMARK_TNA|2026-09-28|2026-10-01");
		assertThat(reader.macro(MacroVariable.MONTHLY_INFLATION, LocalDate.parse("2026-09-30"), MACRO).orElseThrow()
			.value()).isEqualByComparingTo("0.019");
		assertThat(reader.macroCount()).isEqualTo(2);

		// One BACKFILL run per source and day.
		for (LocalDate day : DAYS) {
			assertThat(reader.sourceRunCount(day)).as("source runs of %s: three sources, plus the summary on the last day", day)
				.isEqualTo(day.equals(TO) ? 4 : 3);
			SourceRunRow funds = run(day, FUNDS);
			assertThat(funds.mode()).isEqualTo("BACKFILL");
			assertThat(funds.status()).isEqualTo("OK");
			assertThat(funds.rows()).isEqualTo(fixtureRows());
			assertThat(funds.failure()).isNull();
			SourceRunRow mep = run(day, MEP);
			assertThat(mep.mode()).isEqualTo("BACKFILL");
			assertThat(mep.status()).isEqualTo("OK");
			assertThat(mep.rows()).isEqualTo(1);
			SourceRunRow macro = run(day, MACRO);
			assertThat(macro.mode()).isEqualTo("BACKFILL");
			assertThat(macro.status()).isEqualTo("OK");
			assertThat(macro.rows()).isEqualTo(day.equals(LocalDate.parse("2026-09-30")) ? 2 : 0);
		}

		// The run's own row on its last day: every row it inserted (4 days of funds, 4 MEP quotes, 2 macro points).
		SourceRunRow summary = run(TO, SUMMARY);
		assertThat(summary.mode()).isEqualTo("BACKFILL");
		assertThat(summary.status()).isEqualTo("OK");
		assertThat(summary.rows()).isEqualTo(4 * fixtureRows() + 4 + 2);
		assertThat(summary.failure()).isEqualTo("history backfill 2026-09-28..2026-10-01");

		// The polite delay: between consecutive days only, as configured.
		assertThat(properties.delay()).isEqualTo(Duration.ofMillis(500));
		assertThat(StubSources.PAUSES).containsExactly(properties.delay(), properties.delay(), properties.delay());

		// The data version moved, so every cache keyed by it is invalidated.
		assertThat(dataVersion.current()).isNotEqualTo(versionBefore);
	}

	@Test
	void liveDataWinsEveryStoredRowAndEveryLiveRunSurvivesTheBackfill() {
		LocalDate liveDay = LocalDate.parse("2026-09-29");
		LocalDate macroDay = LocalDate.parse("2026-09-30");
		FundQuote live = IngestFixtures.fundDay(liveDay).stream()
			.filter(quote -> quote.name().equals(MERCADO_FONDO))
			.map(quote -> new FundQuote(quote.source(), quote.name(), quote.fund(), quote.shareClass(),
					quote.horizon(), quote.segment(), liveDay, new BigDecimal("1.5"), quote.ccp(), quote.patrimonio(),
					LIVE_AT, quote.confidence(), quote.flags()))
			.findFirst()
			.orElseThrow();
		fundWriter.write(List.of(live), liveDay);
		runWriter.record(liveDay, FUNDS, RunMode.LIVE, RunStatus.OK, 2, null, LIVE_AT);
		dollarWriter.write(List.of(new DollarQuote(DolarApiAdapter.SOURCE, new BigDecimal("1534.2"),
				new BigDecimal("1539.4"), null, LIVE_AT, Confidence.AGGREGATOR_AUTOMATIC, List.of())), macroDay);
		macroWriter.write(List.of(new MacroObservation(BcraMonetariasClient.SOURCE, MacroVariable.MONTHLY_INFLATION,
				macroDay, new BigDecimal("0.5"), LIVE_AT, Confidence.OFFICIAL_AUTOMATIC, List.of())), macroDay);

		runBackfill(FROM, TO);

		assertThat(reader.fundQuote(FUNDS, MERCADO_FONDO, liveDay).orElseThrow().vcp()).isEqualByComparingTo("1.5");
		assertThat(reader.fundQuote(FUNDS, MERCADO_FONDO, liveDay).orElseThrow().fetchedAt()).isEqualTo(LIVE_AT);
		SourceRunRow liveRun = run(liveDay, FUNDS);
		assertThat(liveRun.mode()).isEqualTo("LIVE");
		assertThat(liveRun.rows()).isEqualTo(2);
		assertThat(liveRun.recordedAt()).isEqualTo(LIVE_AT);
		assertThat(reader.dollar(DOLAR_API, macroDay).orElseThrow().buy()).isEqualByComparingTo("1534.2");
		assertThat(reader.dollar(MEP, macroDay)).as("the history is stored next to the live row").isPresent();
		assertThat(reader.macro(MacroVariable.MONTHLY_INFLATION, macroDay, MACRO).orElseThrow().value())
			.isEqualByComparingTo("0.5");
		assertThat(run(FROM, FUNDS).mode()).as("the other days are still recorded as backfill").isEqualTo("BACKFILL");
	}

	@Test
	void aFailingDayIsRecordedAsFailedAndTheOtherDaysAndKindsGoOn() {
		LocalDate failing = LocalDate.parse("2026-09-29");
		StubSources.FUND_HISTORY_FAILING_DAYS.add(failing);

		JobExecution execution = runBackfill(FROM, TO);

		assertThat(execution.getStatus()).as("a failing source never fails the job").isEqualTo(BatchStatus.COMPLETED);
		SourceRunRow failed = run(failing, FUNDS);
		assertThat(failed.mode()).isEqualTo("BACKFILL");
		assertThat(failed.status()).isEqualTo("FAILED");
		assertThat(failed.rows()).isZero();
		assertThat(failed.failure()).contains("stub fund history is down");
		assertThat(reader.fundQuote(FUNDS, MERCADO_FONDO, failing)).isEmpty();
		assertThat(run(FROM, FUNDS).status()).isEqualTo("OK");
		assertThat(run(TO, FUNDS).status()).isEqualTo("OK");
		assertThat(run(failing, MEP).status()).isEqualTo("OK");

		// The source recovers: running the range again replaces the FAILED backfill run with an OK one.
		StubSources.FUND_HISTORY_FAILING_DAYS.clear();
		runBackfill(FROM, TO);

		SourceRunRow recovered = run(failing, FUNDS);
		assertThat(recovered.status()).isEqualTo("OK");
		assertThat(recovered.rows()).isEqualTo(fixtureRows());
		assertThat(recovered.failure()).isNull();
		assertThat(reader.fundQuote(FUNDS, MERCADO_FONDO, failing)).isPresent();
	}

	@Test
	void aDayWithoutQuotesIsAnOkRunOfZeroRowsThatSaysSo() {
		LocalDate weekend = LocalDate.parse("2026-09-29");
		StubSources.FUND_HISTORY_EMPTY_DAYS.add(weekend);

		runBackfill(FROM, TO);

		SourceRunRow run = run(weekend, FUNDS);
		assertThat(run.mode()).isEqualTo("BACKFILL");
		assertThat(run.status()).isEqualTo("OK");
		assertThat(run.rows()).isZero();
		assertThat(run.failure()).isEqualTo("the source has no quotes for this date");
		assertThat(reader.fundQuote(FUNDS, MERCADO_FONDO, weekend)).isEmpty();
	}

	@Test
	void aFailingMepSeriesIsRecordedForEveryDayWithoutStoppingTheOtherKinds() {
		StubSources.DOLLAR_SERIES_FAILS = true;

		JobExecution execution = runBackfill(FROM, TO);

		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		for (LocalDate day : DAYS) {
			SourceRunRow mep = run(day, MEP);
			assertThat(mep.mode()).isEqualTo("BACKFILL");
			assertThat(mep.status()).isEqualTo("FAILED");
			assertThat(mep.rows()).isZero();
			assertThat(mep.failure()).contains("stub MEP series is down");
			assertThat(run(day, FUNDS).status()).isEqualTo("OK");
		}
		assertThat(reader.dollarCount()).isZero();
		assertThat(StubSources.DOLLAR_SERIES_CALLS).as("read once, not once per day").hasValue(1);
	}

	@Test
	void aMacroVariableThatFailsDegradesTheDaysAndAllOfThemFailingFailsThem() {
		StubSources.MACRO_RANGE_FAILING.add(MacroVariable.PSP_BENCHMARK_TNA);

		runBackfill(FROM, TO);

		for (LocalDate day : DAYS) {
			SourceRunRow macro = run(day, MACRO);
			assertThat(macro.status()).isEqualTo("DEGRADED");
			assertThat(macro.failure()).contains("PSP_BENCHMARK_TNA");
		}
		assertThat(reader.macro(MacroVariable.MONTHLY_INFLATION, LocalDate.parse("2026-09-30"), MACRO)).isPresent();

		StubSources.MACRO_RANGE_FAILING.addAll(List.of(MacroVariable.values()));
		runBackfill(FROM, TO);

		for (LocalDate day : DAYS) {
			assertThat(run(day, MACRO).status()).isEqualTo("FAILED");
			assertThat(run(day, MACRO).rows()).isZero();
		}
	}

	@Test
	void runningTheSameRangeAgainIsANewInstanceThatWritesNothingMore() {
		runBackfill(FROM, TO);
		int quotes = reader.fundQuoteCount();
		int dollars = reader.dollarCount();
		int macros = reader.macroCount();

		JobExecution second = runBackfill(FROM, TO);

		assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(reader.fundQuoteCount()).isEqualTo(quotes);
		assertThat(reader.dollarCount()).isEqualTo(dollars);
		assertThat(reader.macroCount()).isEqualTo(macros);
		for (LocalDate day : DAYS) {
			assertThat(reader.sourceRunCount(day)).isEqualTo(day.equals(TO) ? 4 : 3);
			assertThat(run(day, FUNDS).rows()).as("nothing new was inserted").isZero();
			assertThat(run(day, FUNDS).status()).isEqualTo("OK");
		}
		assertThat(run(TO, SUMMARY).rows()).isZero();
	}

	@Test
	void aBackfillThatOnlyFillsGapsUnderLiveRunsStillMovesTheDataVersion() {
		for (LocalDate day : DAYS) {
			for (String source : List.of(FUNDS, MEP, MACRO)) {
				runWriter.record(day, source, RunMode.LIVE, RunStatus.FAILED, 0, "the live source was down", LIVE_AT);
			}
		}
		String before = dataVersion.current();

		runBackfill(FROM, TO);

		assertThat(reader.fundQuote(FUNDS, MERCADO_FONDO, FROM)).as("the gap is filled").isPresent();
		for (LocalDate day : DAYS) {
			for (String source : List.of(FUNDS, MEP, MACRO)) {
				SourceRunRow live = run(day, source);
				assertThat(live.mode()).as("%s on %s stays a live run", source, day).isEqualTo("LIVE");
				assertThat(live.status()).isEqualTo("FAILED");
				assertThat(live.recordedAt()).isEqualTo(LIVE_AT);
			}
		}
		assertThat(dataVersion.current()).as("caches keyed by the version see the new history").isNotEqualTo(before);
	}

	@Test
	void noAlertEvaluationAndNoRebuildHookRunAfterABackfill() {
		// Control: the daily job for today does evaluate the alerts and call the hook, so the counters can see them.
		LocalDate today = LocalDate.now(Instrumentation.CLOCK);
		ingestionService.run(today, RunMode.LIVE);
		assertThat(Instrumentation.EVALUATED).as("control: the daily job evaluates today").containsExactly(today);
		assertThat(Instrumentation.HOOK_POSTS).as("control: the daily job rebuilds the site").hasValue(1);

		runBackfill(FROM, TO);

		assertThat(Instrumentation.EVALUATED).containsExactly(today);
		assertThat(Instrumentation.HOOK_POSTS).hasValue(1);
	}

	/** An alert evaluator and a rebuild hook that only count their calls, so a listener on the job would show. */
	@TestConfiguration(proxyBeanMethods = false)
	static class Instrumentation {

		static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

		/** 2026-11-01 12:00 in Buenos Aires: after every day the tests backfill, and the day the control ingests. */
		static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T15:00:00Z"), ZONE);
		static final List<LocalDate> EVALUATED = new CopyOnWriteArrayList<>();
		static final AtomicInteger HOOK_POSTS = new AtomicInteger();
		static MockRestServiceServer hook;

		static void reset() {
			EVALUATED.clear();
			HOOK_POSTS.set(0);
			hook.reset();
			hook.expect(manyTimes(), requestTo(HOOK_URL)).andExpect(method(HttpMethod.POST)).andRespond(request -> {
				HOOK_POSTS.incrementAndGet();
				return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
			});
		}

		@Bean
		@Primary
		AlertEvaluator recordingAlertEvaluator() {
			return EVALUATED::add;
		}

		@Bean
		@Primary
		RestClient rebuildHookMockRestClient() {
			RestClient.Builder builder = RestClient.builder();
			hook = MockRestServiceServer.bindTo(builder).build();
			return builder.build();
		}

		@Bean
		@Primary
		Clock fixedArgentinaClock() {
			return CLOCK;
		}
	}
}
