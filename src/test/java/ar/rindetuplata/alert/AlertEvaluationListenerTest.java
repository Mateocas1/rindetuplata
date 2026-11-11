package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ar.rindetuplata.ingest.IngestionService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

/**
 * Unit rules of the alert evaluation listener: it evaluates once after a COMPLETED
 * {@code dailyIngestionJob}, for the job's own data date; a FAILED job is never evaluated; an evaluator that
 * throws is logged and the job status is untouched, because the ingestion already wrote its data. Alerts
 * are about today, so a completed job evaluates only when its data date is today in America/Argentina/Buenos_Aires;
 * a replay or a late run of a past date never compares old data with newer alert state.
 */
class AlertEvaluationListenerTest {

	private static final LocalDate DATA_DATE = LocalDate.parse("2026-10-14");

	private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

	/** 2026-10-14 12:00 in Buenos Aires: today is the job's data date. */
	private static final Clock TODAY_IS_THE_DATA_DATE = Clock.fixed(Instant.parse("2026-10-14T15:00:00Z"), BUENOS_AIRES);

	/** Rule 1: one evaluation, for the data date the job ran. */
	@Test
	void aCompletedJobEvaluatesItsDataDateOnce() {
		RecordingEvaluator evaluator = new RecordingEvaluator();

		new AlertEvaluationListener(evaluator, TODAY_IS_THE_DATA_DATE).afterJob(execution(BatchStatus.COMPLETED));

		assertThat(evaluator.calls).containsExactly(DATA_DATE);
	}

	/** Rule 2: a FAILED ingestion is not a reason to evaluate anything. */
	@Test
	void aFailedJobIsNeverEvaluated() {
		RecordingEvaluator evaluator = new RecordingEvaluator();

		new AlertEvaluationListener(evaluator, TODAY_IS_THE_DATA_DATE).afterJob(execution(BatchStatus.FAILED));

		assertThat(evaluator.calls).isEmpty();
	}

	/** Rule 3: an evaluator exception is logged, never rethrown, and the job stays COMPLETED. */
	@Test
	void anEvaluatorFailureIsLoggedAndTheJobStaysCompleted() {
		AlertEvaluator failing = dataDate -> {
			throw new IllegalStateException("evaluation exploded");
		};
		JobExecution completed = execution(BatchStatus.COMPLETED);

		assertThatCode(() -> new AlertEvaluationListener(failing, TODAY_IS_THE_DATA_DATE).afterJob(completed)).doesNotThrowAnyException();

		assertThat(completed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
	}

	/** Rule 4: a completed job of a past data date (a replay, a late run) is never evaluated. */
	@Test
	void aCompletedJobOfAPastDayIsNeverEvaluated() {
		RecordingEvaluator evaluator = new RecordingEvaluator();
		Clock nextDay = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), BUENOS_AIRES);

		new AlertEvaluationListener(evaluator, nextDay).afterJob(execution(BatchStatus.COMPLETED));

		assertThat(evaluator.calls).isEmpty();
	}

	/** Rule 5: nor is a data date that has not happened yet. */
	@Test
	void aCompletedJobOfAFutureDayIsNeverEvaluated() {
		RecordingEvaluator evaluator = new RecordingEvaluator();
		Clock dayBefore = Clock.fixed(Instant.parse("2026-10-13T15:00:00Z"), BUENOS_AIRES);

		new AlertEvaluationListener(evaluator, dayBefore).afterJob(execution(BatchStatus.COMPLETED));

		assertThat(evaluator.calls).isEmpty();
	}

	/**
	 * Rule 6: "today" is the day in Buenos Aires whatever zone the clock carries. At 2026-10-15T01:00Z it is still
	 * 22:00 of the 14th there, so the 14th is today and the 15th (the UTC date) is not.
	 */
	@Test
	void todayIsTheArgentineDayNotTheClockZonesDay() {
		RecordingEvaluator evaluator = new RecordingEvaluator();
		Clock utcClock = Clock.fixed(Instant.parse("2026-10-15T01:00:00Z"), ZoneId.of("UTC"));
		AlertEvaluationListener listener = new AlertEvaluationListener(evaluator, utcClock);

		listener.afterJob(execution(BatchStatus.COMPLETED, DATA_DATE));
		listener.afterJob(execution(BatchStatus.COMPLETED, DATA_DATE.plusDays(1)));

		assertThat(evaluator.calls).containsExactly(DATA_DATE);
	}

	private static JobExecution execution(BatchStatus status) {
		return execution(status, DATA_DATE);
	}

	private static JobExecution execution(BatchStatus status, LocalDate dataDate) {
		JobExecution execution = new JobExecution(1L, new JobInstance(1L, IngestionService.JOB_NAME),
				new JobParametersBuilder()
					.addString(IngestionService.DATA_DATE_PARAMETER, dataDate.toString())
					.toJobParameters());
		execution.setStatus(status);
		return execution;
	}

	/** A fake evaluator that records the dates it was asked for, so the listener's routing is assertable. */
	private static final class RecordingEvaluator implements AlertEvaluator {

		private final List<LocalDate> calls = new ArrayList<>();

		@Override
		public void evaluate(LocalDate dataDate) {
			calls.add(dataDate);
		}

	}

}
