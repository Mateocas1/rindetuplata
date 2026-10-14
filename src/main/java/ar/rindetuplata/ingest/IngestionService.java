package ar.rindetuplata.ingest;

import ar.rindetuplata.store.RunMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Public facade of the daily ingestion for T5/T6 and the scheduler: one job instance per data date, live or
 * replayed from the T9 archive. A date whose instance already COMPLETED is a no-op reported as such; a date whose
 * instance FAILED restarts it, and only its unfinished steps run again. Unexpected internal errors (a missing
 * manifest, a defect in a step) surface as a FAILED result with the failure messages, never as an exception to the
 * caller.
 */
@Component
public class IngestionService {

	public static final String JOB_NAME = "dailyIngestionJob";

	public static final String DATA_DATE_PARAMETER = "dataDate";

	public static final String MODE_PARAMETER = "mode";

	private final JobOperator jobOperator;

	private final Job dailyIngestionJob;

	public IngestionService(JobOperator jobOperator, @Qualifier(JOB_NAME) Job dailyIngestionJob) {
		this.jobOperator = jobOperator;
		this.dailyIngestionJob = dailyIngestionJob;
	}

	public IngestionResult run(LocalDate dataDate, RunMode mode) {
		JobParameters parameters = parameters(dataDate, mode);
		try {
			JobExecution execution = jobOperator.start(dailyIngestionJob, parameters);
			return new IngestionResult(dataDate, mode, execution.getStatus(), false, execution.getId(),
					failureMessages(execution));
		}
		catch (JobInstanceAlreadyCompleteException e) {
			return IngestionResult.alreadyCompleted(dataDate, mode);
		}
		catch (JobExecutionAlreadyRunningException | JobRestartException | InvalidJobParametersException e) {
			throw new IllegalStateException(
					"cannot launch " + JOB_NAME + " for " + dataDate + " in " + mode + ": " + e.getMessage(), e);
		}
	}

	/**
	 * The job parameters that identify one ingestion: the data date identifies the instance, the mode does not, so
	 * replaying a date after a live run restarts the same instance instead of creating a second one (D4).
	 */
	public static JobParameters parameters(LocalDate dataDate, RunMode mode) {
		return new JobParametersBuilder().addString(DATA_DATE_PARAMETER, dataDate.toString())
			.addString(MODE_PARAMETER, mode.name(), false)
			.toJobParameters();
	}

	private static List<String> failureMessages(JobExecution execution) {
		if (execution.getStatus() == BatchStatus.COMPLETED) {
			return List.of();
		}
		return execution.getAllFailureExceptions()
			.stream()
			.map(failure -> failure.getClass().getSimpleName() + ": " + failure.getMessage())
			.toList();
	}
}
