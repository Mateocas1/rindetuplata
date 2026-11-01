package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.account.AccountRepository;
import ar.rindetuplata.ingest.IngestionResult;
import ar.rindetuplata.ingest.IngestionService;
import ar.rindetuplata.store.RunMode;
import ar.rindetuplata.store.StoreTestConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * T6c end-to-end proof of the registration (S3, S4): the real {@code dailyIngestionJob} replays the recorded
 * 2026-10-14 archive and the listener registered on it evaluates that same day, so the matches and their
 * {@code AlertTriggered} events exist without anybody calling the evaluator. A job that FAILS (a day with no
 * manifest) never evaluates: no match, no event. T4a (L33 a): the evaluation happens only when the job's data date is
 * today in Buenos Aires, so the same replay of a day that is not today (here the 13th, a copy of the recorded
 * archive) COMPLETES and writes no match and no outbox event.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *"
})
@Import({ StoreTestConfiguration.class, AlertEvaluationJobIntegrationTest.MovableClock.Config.class })
class AlertEvaluationJobIntegrationTest {

	/** A day the recorded archive has, so the replay COMPLETES. */
	static final LocalDate ARCHIVED_DAY = LocalDate.parse("2026-10-14");

	/** The day before: a copy of the recorded archive under a temporary checkout, a day that is not today. */
	static final LocalDate PAST_DAY = LocalDate.parse("2026-10-13");

	/** Nothing is archived for this day, so the replay FAILS with a missing manifest. */
	static final LocalDate NO_MANIFEST_DAY = LocalDate.parse("2026-10-16");

	private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");

	/** 2026-10-14 12:00 in Buenos Aires: the recorded archive's day is today. */
	private static final Instant TODAY_IS_THE_ARCHIVED_DAY = Instant.parse("2026-10-14T15:00:00Z");

	private static final Path ARCHIVE = archiveWithAPastDay();

	@DynamicPropertySource
	static void archive(DynamicPropertyRegistry registry) {
		registry.add("rindetuplata.ingest.archive-dir", ARCHIVE::toString);
	}

	@Autowired
	MovableClock clock;

	@Autowired
	IngestionService ingestionService;

	@Autowired
	AlertService alerts;

	@Autowired
	AccountRepository accounts;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void restoreTheClock() {
		clock.set(TODAY_IS_THE_ARCHIVED_DAY);
	}

	@Test
	void aCompletedJobEvaluatesTheDayItJustWrote() {
		clock.set(TODAY_IS_THE_ARCHIVED_DAY);
		long rule = rule("job-completed@example.com", "0.001");

		IngestionResult result = ingestionService.run(ARCHIVED_DAY, RunMode.REPLAY);

		assertThat(result.status()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(matchCount(rule)).as("the listener evaluated the day the job wrote").isPositive();
		assertThat(outboxCount(rule)).as("every match enqueued exactly one event").isEqualTo(matchCount(rule));
		assertThat(captureDates(rule)).as("the matches carry the job's data date").containsExactly("2026-10-14");
	}

	@Test
	void aCompletedJobOfAPastDayWritesNoMatchAndNoOutboxEvent() {
		clock.set(TODAY_IS_THE_ARCHIVED_DAY);
		long rule = rule("job-past@example.com", "0.001");

		IngestionResult result = ingestionService.run(PAST_DAY, RunMode.REPLAY);

		assertThat(result.status()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(result.alreadyCompleted()).isFalse();
		assertThat(jdbc.sql("select count(*) from source_run where capture_date = :day")
			.param("day", PAST_DAY)
			.query(Long.class)
			.single()).as("the ingestion itself ran and wrote its source runs").isPositive();
		assertThat(matchCount(rule)).as("a past day is never compared with today's alert state").isZero();
		assertThat(outboxCount(rule)).isZero();
	}

	@Test
	void aFailedJobNeverEvaluates() {
		clock.set(TODAY_IS_THE_ARCHIVED_DAY);
		long rule = rule("job-failed@example.com", "0.001");

		IngestionResult result = ingestionService.run(NO_MANIFEST_DAY, RunMode.REPLAY);

		assertThat(result.status()).isEqualTo(BatchStatus.FAILED);
		assertThat(matchCount(rule)).isZero();
		assertThat(outboxCount(rule)).isZero();
	}

	private long rule(String email, String percent) {
		accounts.findByEmail(email).orElseGet(() -> accounts.create(email, NOW));
		return alerts.create(email, new AlertRequest(null, null, null, new BigDecimal(percent))).id();
	}

	private long matchCount(long rule) {
		return jdbc.sql("select count(*) from alert_match where rule_id = :rule")
			.param("rule", rule)
			.query(Long.class)
			.single();
	}

	private long outboxCount(long rule) {
		return jdbc.sql("select count(*) from outbox_event where (payload->>'ruleId')::bigint = :rule")
			.param("rule", rule)
			.query(Long.class)
			.single();
	}

	private List<String> captureDates(long rule) {
		return jdbc.sql("select distinct payload->>'captureDate' from outbox_event where (payload->>'ruleId')::bigint = :rule")
			.param("rule", rule)
			.query(String.class)
			.list();
	}

	/** A temporary checkout of the data branch holding the recorded 2026-10-14 day and a copy of it as the 13th. */
	private static Path archiveWithAPastDay() {
		try {
			Path root = Files.createTempDirectory("alert-job-archive");
			Path recorded = Path.of("src/test/resources/archive/raw/2026/10/14");
			for (String day : List.of("14", "13")) {
				Path target = Files.createDirectories(root.resolve("raw/2026/10").resolve(day));
				try (var files = Files.list(recorded)) {
					for (Path file : files.toList()) {
						Files.copy(file, target.resolve(file.getFileName()));
					}
				}
			}
			return root;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** The clock of the context: the production code reads "today" from it, the test moves it. */
	static final class MovableClock extends Clock {

		private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

		private volatile Instant instant = TODAY_IS_THE_ARCHIVED_DAY;

		void set(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZONE;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return Clock.fixed(instant, zone);
		}

		@Override
		public Instant instant() {
			return instant;
		}

		@TestConfiguration(proxyBeanMethods = false)
		static class Config {

			@Bean
			@Primary
			MovableClock movableClock() {
				return new MovableClock();
			}
		}
	}

}
