package ar.rindetuplata.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.RindetuplataApplication;
import ar.rindetuplata.source.dollar.DolarApiAdapter;
import ar.rindetuplata.source.plazofijo.BcraPlazoFijoAdapter;
import ar.rindetuplata.store.InstrumentKind;
import ar.rindetuplata.store.OfficialPlazoFijoWriter;
import ar.rindetuplata.store.RunMode;
import ar.rindetuplata.store.StoreReader;
import ar.rindetuplata.store.StoreReader.SourceRunRow;
import ar.rindetuplata.store.StoreTestConfiguration;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * T14a (S20): with {@code rindetuplata.ingest.run-on-startup=true} the application runs today's LIVE ingestion,
 * today being the day in {@code rindetuplata.ingest.zone} read through the same clock the scheduler uses, once, as
 * soon as it is ready. Running it again for a day whose instance already COMPLETED is the existing idempotency: a
 * no-op. A failing run is logged and the application keeps working.
 */
@Import({ StoreTestConfiguration.class, StubSourcesConfiguration.class, StartupIngestionTest.MutableTestClock.class })
@SpringBootTest(properties = { "rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.ingest.run-on-startup=true" })
class StartupIngestionTest {

	/** The same instant in UTC is 2027-02-10: only the Argentine zone makes "today" 2027-02-09. */
	static final Instant BOOT_INSTANT = Instant.parse("2027-02-10T02:00:00Z");

	static final LocalDate BOOT_DAY = LocalDate.parse("2027-02-09");

	static final LocalDate NEXT_DAY = LocalDate.parse("2027-02-10");

	/** The day the failing-run test moves the clock to: no other test touches it. */
	static final Instant FAILING_INSTANT = Instant.parse("2027-02-12T02:00:00Z");

	static final LocalDate FAILING_DAY = LocalDate.parse("2027-02-11");

	static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(60);

	/**
	 * The clock the tests can move to another day. The bean name must be {@code clock}: the application's
	 * {@code apiClock} and this one are both candidates for the API's and the scheduler's unqualified
	 * {@code Clock} injections, and the parameter name is what disambiguates them.
	 */
	static final MutableClock CLOCK = new MutableClock(BOOT_INSTANT, ZoneId.of("America/Argentina/Buenos_Aires"));

	static final String OFFICIAL = BcraPlazoFijoAdapter.SOURCE.id();
	static final String DOLLAR = DolarApiAdapter.SOURCE.id();

	@Autowired
	StoreReader reader;

	@Autowired
	JobRepository jobRepository;

	@Autowired
	IngestionService ingestionService;

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	ConfigurableApplicationContext context;

	private final List<ListAppender<ILoggingEvent>> appenders = new ArrayList<>();

	@AfterEach
	void detachAppenders() {
		Logger logger = (Logger) LoggerFactory.getLogger(IngestionScheduler.class);
		appenders.forEach(logger::detachAppender);
		appenders.clear();
	}

	@Test
	void theReadyEventIngestsTodayOnceInBuenosAires() {
		SourceRunRow dollar = awaitSourceRun(DOLLAR, BOOT_DAY);

		assertThat(dollar.mode()).isEqualTo("LIVE");
		assertThat(dollar.status()).isEqualTo("OK");
		assertThat(reader.sourceRunCount(BOOT_DAY)).isEqualTo(8);
		assertThat(reader
			.snapshot(InstrumentKind.PLAZO_FIJO_OFFICIAL, OFFICIAL,
					OfficialPlazoFijoWriter.naturalKey(IngestFixtures.officialRates().get(0)), BOOT_DAY))
			.isPresent();
		assertThat(reader.sourceRunCount(NEXT_DAY)).as("2027-02-10 is the UTC day, never today in Buenos Aires")
			.isZero();

		JobInstance instance = jobRepository.getJobInstance(IngestionService.JOB_NAME,
				IngestionService.parameters(BOOT_DAY, RunMode.LIVE));
		assertThat(jobRepository.getJobExecutions(instance)).as("one startup ingestion, not two").hasSize(1);
	}

	@Test
	void aSecondReadyEventOnAnAlreadyCompletedDayIsANoOp() {
		awaitSourceRun(DOLLAR, BOOT_DAY);
		CLOCK.set(BOOT_INSTANT);
		int sourceRuns = reader.sourceRunCount(BOOT_DAY);
		int executions = executions(BOOT_DAY);
		int sourceCalls = StubSources.PLAZO_FIJO_CALLS.get();
		ListAppender<ILoggingEvent> appender = captureSchedulerLog();
		int logged = appender.list.size();

		publisher.publishEvent(readyEvent());

		await("the no-op to be logged", () -> logged(appender, logged, "already completed: true"));
		assertThat(StubSources.PLAZO_FIJO_CALLS).as("a no-op never asks a source again").hasValue(sourceCalls);
		assertThat(reader.sourceRunCount(BOOT_DAY)).isEqualTo(sourceRuns);
		assertThat(executions(BOOT_DAY)).isEqualTo(executions);
	}

	@Test
	void aFailingRunIsLoggedAndTheApplicationKeepsWorking() {
		awaitSourceRun(DOLLAR, BOOT_DAY);
		CLOCK.set(FAILING_INSTANT);
		StubSources.reset();
		StubSources.CRYPTO_FAILS_ONCE = true;
		ListAppender<ILoggingEvent> appender = captureSchedulerLog();
		int logged = appender.list.size();

		publisher.publishEvent(readyEvent());

		await("the failing run to finish", () -> logged(appender, logged, "finished: FAILED"));
		assertThat(reader.dollar(DOLLAR, FAILING_DAY))
			.as("the failing crypto step stops the job before the dollar step")
			.isEmpty();
		assertThat(executions(FAILING_DAY)).isEqualTo(1);

		IngestionResult restarted = ingestionService.run(FAILING_DAY, RunMode.LIVE);

		assertThat(restarted.status()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(reader.dollar(DOLLAR, FAILING_DAY)).as("the application is still ingesting").isPresent();
	}

	private ApplicationReadyEvent readyEvent() {
		return new ApplicationReadyEvent(new SpringApplication(RindetuplataApplication.class), new String[0], context,
				Duration.ZERO);
	}

	private int executions(LocalDate date) {
		JobInstance instance = jobRepository.getJobInstance(IngestionService.JOB_NAME,
				IngestionService.parameters(date, RunMode.LIVE));
		return instance == null ? 0 : jobRepository.getJobExecutions(instance).size();
	}

	private ListAppender<ILoggingEvent> captureSchedulerLog() {
		Logger logger = (Logger) LoggerFactory.getLogger(IngestionScheduler.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		appenders.add(appender);
		return appender;
	}

	private static boolean logged(ListAppender<ILoggingEvent> appender, int from, String fragment) {
		return appender.list.subList(Math.min(from, appender.list.size()), appender.list.size())
			.stream()
			.anyMatch(event -> event.getFormattedMessage().contains(fragment));
	}

	private SourceRunRow awaitSourceRun(String sourceId, LocalDate date) {
		return awaitFor("source_run for " + sourceId + " on " + date,
				() -> reader.sourceRun(date, sourceId).orElse(null));
	}

	/** Waits until the condition holds, so a probe that answers {@code false} is never mistaken for a result. */
	private void await(String what, BooleanSupplier condition) {
		long deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
			sleep();
		}
		assertThat(condition.getAsBoolean()).as("waited %s for %s", AWAIT_TIMEOUT, what).isTrue();
	}

	private <T> T awaitFor(String what, Supplier<T> probe) {
		long deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
		T value = probe.get();
		while (value == null && System.nanoTime() < deadline) {
			sleep();
			value = probe.get();
		}
		assertThat(value).as("waited %s for %s", AWAIT_TIMEOUT, what).isNotNull();
		return value;
	}

	private static void sleep() {
		try {
			Thread.sleep(50);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	/** A clock the tests can move to another day: the startup run reads "today" through it. */
	static final class MutableClock extends Clock {

		private final AtomicReference<Instant> instant;

		private final ZoneId zone;

		MutableClock(Instant instant, ZoneId zone) {
			this.instant = new AtomicReference<>(instant);
			this.zone = zone;
		}

		void set(Instant instant) {
			this.instant.set(instant);
		}

		@Override
		public ZoneId getZone() {
			return zone;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return new MutableClock(instant.get(), zone);
		}

		@Override
		public Instant instant() {
			return instant.get();
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class MutableTestClock {

		@Bean
		Clock clock() {
			return CLOCK;
		}
	}
}
