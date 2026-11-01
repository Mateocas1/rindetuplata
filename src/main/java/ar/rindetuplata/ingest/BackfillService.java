package ar.rindetuplata.ingest;

import ar.rindetuplata.api.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Starts {@code historyBackfillJob} for the admin endpoint (T4a, L33 a). A request is checked first (inclusive range,
 * at most {@value #MAX_DAYS} days, {@code to} not after yesterday in the application's Argentine day), then the job
 * runs on its own thread so the endpoint answers 202 with the job id while it goes on. One run at a time: a second
 * start while one runs is a {@link BackfillAlreadyRunningException}. The guard is in memory, which fits the single API
 * instance (S16) and cannot be stuck by a crashed run, because it dies with the process. Every run is a new job
 * instance (a random {@code runId} identifies it), so repeating a range is allowed and idempotent by its insert-only
 * writes, never a "job instance already complete" error.
 */
@Component
public class BackfillService {

	public static final String JOB_NAME = "historyBackfillJob";

	/** The longest range of one run, both ends included. */
	public static final int MAX_DAYS = 366;

	private static final long START_TIMEOUT_SECONDS = 30;

	private static final Logger log = LoggerFactory.getLogger(BackfillService.class);

	private final JobOperator jobOperator;

	private final Job job;

	private final BackfillJobListener listener;

	private final Clock clock;

	private final AtomicBoolean running = new AtomicBoolean();

	BackfillService(JobOperator jobOperator, @Qualifier(JOB_NAME) Job job, BackfillJobListener listener,
			Clock clock) {
		this.jobOperator = jobOperator;
		this.job = job;
		this.listener = listener;
		this.clock = clock;
	}

	/**
	 * @throws ApiException (400) when the range is inverted, longer than {@value #MAX_DAYS} days or ends after yesterday
	 * @throws BackfillAlreadyRunningException when another run has not finished
	 */
	public BackfillStarted start(LocalDate from, LocalDate to) {
		validate(from, to);
		if (!running.compareAndSet(false, true)) {
			throw new BackfillAlreadyRunningException();
		}
		CompletableFuture<Long> jobId = new CompletableFuture<>();
		try {
			listener.expect(jobId);
			JobParameters parameters = new JobParametersBuilder()
				.addString(BackfillJobConfiguration.FROM_PARAMETER, from.toString())
				.addString(BackfillJobConfiguration.TO_PARAMETER, to.toString())
				.addString("runId", UUID.randomUUID().toString())
				.toJobParameters();
			Thread.ofVirtual().name("history-backfill").start(() -> execute(parameters, jobId));
		}
		catch (RuntimeException e) {
			running.set(false);
			throw e;
		}
		try {
			return new BackfillStarted(jobId.get(START_TIMEOUT_SECONDS, TimeUnit.SECONDS), from, to);
		}
		catch (ExecutionException e) {
			throw new IllegalStateException("the history backfill could not start: " + e.getCause().getMessage(),
					e.getCause());
		}
		catch (TimeoutException e) {
			throw new IllegalStateException("the history backfill did not start within " + START_TIMEOUT_SECONDS + " s", e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted while the history backfill was starting", e);
		}
	}

	/** Whether a run has started and not finished. */
	public boolean running() {
		return running.get();
	}

	private void execute(JobParameters parameters, CompletableFuture<Long> jobId) {
		String range = parameters.getString(BackfillJobConfiguration.FROM_PARAMETER) + ".."
				+ parameters.getString(BackfillJobConfiguration.TO_PARAMETER);
		try {
			JobExecution execution = jobOperator.start(job, parameters);
			jobId.complete(execution.getId());
			log.info("history backfill {} finished: {}", range, execution.getStatus());
		}
		catch (Exception e) {
			jobId.completeExceptionally(e);
			log.error("history backfill {} failed", range, e);
		}
		finally {
			running.set(false);
		}
	}

	private void validate(LocalDate from, LocalDate to) {
		if (from.isAfter(to)) {
			throw ApiException.badRequest("from", "'from' (" + from + ") must not be after 'to' (" + to + ")");
		}
		LocalDate yesterday = LocalDate.now(clock).minusDays(1);
		if (to.isAfter(yesterday)) {
			throw ApiException.badRequest("to", "'to' (" + to + ") must not be after yesterday (" + yesterday
					+ "): a backfill never reaches into today");
		}
		long days = ChronoUnit.DAYS.between(from, to) + 1;
		if (days > MAX_DAYS) {
			throw ApiException.badRequest("to", "The range from " + from + " to " + to + " covers " + days
					+ " days; at most " + MAX_DAYS + " are allowed per run ('from' and 'to' are inclusive)");
		}
	}
}
