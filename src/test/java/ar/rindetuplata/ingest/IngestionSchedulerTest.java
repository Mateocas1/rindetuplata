package ar.rindetuplata.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ar.rindetuplata.store.RunMode;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;

/**
 * The run body the daily cron (T4c) and the startup run (T14a) share: "today" is the day in the schedulers's zone,
 * the outcome is logged in one line, and an exception is logged without ever reaching the caller.
 */
class IngestionSchedulerTest {

	static final Clock CLOCK = Clock.fixed(Instant.parse("2027-02-10T02:00:00Z"),
			ZoneId.of("America/Argentina/Buenos_Aires"));

	/** The same instant in UTC is 2027-02-10: only the Argentine zone makes today the 9th. */
	static final LocalDate TODAY = LocalDate.parse("2027-02-09");

	private final IngestionService ingestionService = mock(IngestionService.class);

	private final IngestionScheduler scheduler = new IngestionScheduler(ingestionService, CLOCK);

	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	@BeforeEach
	void captureTheSchedulerLog() {
		appender.start();
		logger().addAppender(appender);
	}

	@AfterEach
	void detachTheSchedulerLog() {
		logger().detachAppender(appender);
	}

	@Test
	void ingestsTodayInTheSchedulersZoneAndLogsTheOutcome() {
		given(ingestionService.run(TODAY, RunMode.LIVE))
			.willReturn(new IngestionResult(TODAY, RunMode.LIVE, BatchStatus.COMPLETED, false, 1L, List.of()));

		scheduler.runToday();

		assertThat(appender.list).singleElement().satisfies(event -> {
			assertThat(event.getLevel()).isEqualTo(Level.INFO);
			assertThat(event.getFormattedMessage())
				.isEqualTo("daily ingestion for 2027-02-09 finished: COMPLETED (already completed: false)");
		});
	}

	@Test
	void aFailingRunIsLoggedAsAnErrorAndNeverPropagates() {
		given(ingestionService.run(any(), any())).willThrow(new IllegalStateException("job is already running"));

		assertThatCode(scheduler::runToday).doesNotThrowAnyException();

		assertThat(appender.list).singleElement().satisfies(event -> {
			assertThat(event.getLevel()).isEqualTo(Level.ERROR);
			assertThat(event.getFormattedMessage()).isEqualTo("daily ingestion for 2027-02-09 failed");
			assertThat(event.getThrowableProxy().getMessage()).isEqualTo("job is already running");
		});
	}

	private static Logger logger() {
		return (Logger) LoggerFactory.getLogger(IngestionScheduler.class);
	}
}
