package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.account.AccountRepository;
import ar.rindetuplata.account.AccountTestConfiguration;
import ar.rindetuplata.ingest.IngestionResult;
import ar.rindetuplata.ingest.IngestionService;
import ar.rindetuplata.store.RunMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * T6c end-to-end proof of the registration (S3, S4): the real {@code dailyIngestionJob} replays the recorded
 * 2026-10-14 archive and the listener registered on it evaluates that same day, so the matches and their
 * {@code AlertTriggered} events exist without anybody calling the evaluator. A job that FAILS (a day with no
 * manifest) never evaluates: no match, no event.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.ingest.archive-dir=src/test/resources/archive"
})
@Import(AccountTestConfiguration.class)
class AlertEvaluationJobIntegrationTest {

	/** A day the recorded archive has, so the replay COMPLETES. */
	static final LocalDate ARCHIVED_DAY = LocalDate.parse("2026-10-14");

	/** Nothing is archived for this day, so the replay FAILS with a missing manifest. */
	static final LocalDate NO_MANIFEST_DAY = LocalDate.parse("2026-10-16");

	private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");

	@Autowired
	IngestionService ingestionService;

	@Autowired
	AlertService alerts;

	@Autowired
	AccountRepository accounts;

	@Autowired
	JdbcClient jdbc;

	@Test
	void aCompletedJobEvaluatesTheDayItJustWrote() {
		long rule = rule("job-completed@example.com", "0.001");

		IngestionResult result = ingestionService.run(ARCHIVED_DAY, RunMode.REPLAY);

		assertThat(result.status()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(matchCount(rule)).as("the listener evaluated the day the job wrote").isPositive();
		assertThat(outboxCount(rule)).as("every match enqueued exactly one event").isEqualTo(matchCount(rule));
		assertThat(captureDates(rule)).as("the matches carry the job's data date").containsExactly("2026-10-14");
	}

	@Test
	void aFailedJobNeverEvaluates() {
		long rule = rule("job-failed@example.com", "0.001");

		IngestionResult result = ingestionService.run(NO_MANIFEST_DAY, RunMode.REPLAY);

		assertThat(result.status()).isEqualTo(BatchStatus.FAILED);
		assertThat(matchCount(rule)).isZero();
		assertThat(outboxCount(rule)).isZero();
	}

	private long rule(String email, String percent) {
		accounts.findByEmail(email).orElseGet(() -> accounts.create(email, NOW));
		return alerts.create(email, new AlertRequest(null, null, new BigDecimal(percent))).id();
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

}
