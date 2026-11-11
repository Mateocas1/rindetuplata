package ar.rindetuplata.ingest;

import java.util.concurrent.CompletableFuture;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;

/**
 * The only listener of {@code historyBackfillJob}: it hands the id of the job execution to the caller of
 * {@link BackfillService#start} as soon as the job begins, so the admin endpoint can answer 202 with it while the run
 * goes on. There is deliberately no alert-evaluation and no rebuild-hook listener: a backfill rewrites the past, and
 * neither the alerts nor the static site are about the past.
 */
class BackfillJobListener implements JobExecutionListener {

	private volatile CompletableFuture<Long> pending;

	/** The caller that wants the id of the run it is about to launch; one run at a time (see the service). */
	void expect(CompletableFuture<Long> jobId) {
		this.pending = jobId;
	}

	@Override
	public void beforeJob(JobExecution jobExecution) {
		CompletableFuture<Long> waiting = pending;
		if (waiting != null) {
			waiting.complete(jobExecution.getId());
		}
	}
}
