package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ar.rindetuplata.ingest.IngestionService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

/**
 * T6c unit rules of the alert evaluation listener (S3, S4): it evaluates once after a COMPLETED
 * {@code dailyIngestionJob}, for the job's own data date; a FAILED job is never evaluated; an evaluator that
 * throws is logged and the job status is untouched, because the ingestion already wrote its data.
 */
class AlertEvaluationListenerTest {

	private static final LocalDate DATA_DATE = LocalDate.parse("2026-10-14");

	/** Rule 1: one evaluation, for the data date the job ran. */
	@Test
	void aCompletedJobEvaluatesItsDataDateOnce() {
		RecordingEvaluator evaluator = new RecordingEvaluator();

		new AlertEvaluationListener(evaluator).afterJob(execution(BatchStatus.COMPLETED));

		assertThat(evaluator.calls).containsExactly(DATA_DATE);
	}

	/** Rule 2: a FAILED ingestion is not a reason to evaluate anything. */
	@Test
	void aFailedJobIsNeverEvaluated() {
		RecordingEvaluator evaluator = new RecordingEvaluator();

		new AlertEvaluationListener(evaluator).afterJob(execution(BatchStatus.FAILED));

		assertThat(evaluator.calls).isEmpty();
	}

	/** Rule 3: an evaluator exception is logged, never rethrown, and the job stays COMPLETED. */
	@Test
	void anEvaluatorFailureIsLoggedAndTheJobStaysCompleted() {
		AlertEvaluator failing = dataDate -> {
			throw new IllegalStateException("evaluation exploded");
		};
		JobExecution completed = execution(BatchStatus.COMPLETED);

		assertThatCode(() -> new AlertEvaluationListener(failing).afterJob(completed)).doesNotThrowAnyException();

		assertThat(completed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
	}

	private static JobExecution execution(BatchStatus status) {
		JobExecution execution = new JobExecution(1L, new JobInstance(1L, IngestionService.JOB_NAME),
				new JobParametersBuilder()
					.addString(IngestionService.DATA_DATE_PARAMETER, DATA_DATE.toString())
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
