package ar.rindetuplata.alert;

import ar.rindetuplata.ingest.IngestionService;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;

/**
 * T6c: after {@code dailyIngestionJob} COMPLETES, evaluates the alert rules for the job's own data date (S3, S4),
 * so the day's ranking decides which rules fire. A FAILED ingestion is never evaluated, and an evaluation that
 * throws is logged and never changes the job, because the ingestion already wrote its data: the alerts are a
 * consequence of the day, not part of it.
 */
public class AlertEvaluationListener implements JobExecutionListener {

	private static final Logger log = LoggerFactory.getLogger(AlertEvaluationListener.class);

	private final AlertEvaluator evaluator;

	public AlertEvaluationListener(AlertEvaluator evaluator) {
		this.evaluator = evaluator;
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
			return;
		}
		String dataDate = jobExecution.getJobParameters().getString(IngestionService.DATA_DATE_PARAMETER);
		try {
			evaluator.evaluate(LocalDate.parse(dataDate));
		}
		catch (RuntimeException e) {
			log.warn("alert evaluation for {} failed: {}", dataDate, e.getMessage(), e);
		}
	}

}
