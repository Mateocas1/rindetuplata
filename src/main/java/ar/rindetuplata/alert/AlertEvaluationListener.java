package ar.rindetuplata.alert;

import ar.rindetuplata.ingest.IngestionService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;

/**
 * After {@code dailyIngestionJob} COMPLETES, evaluates the alert rules for the job's own data date,
 * so the day's ranking decides which rules fire. A FAILED ingestion is never evaluated, and an evaluation that
 * throws is logged and never changes the job, because the ingestion already wrote its data: the alerts are a
 * consequence of the day, not part of it.
 *
 * <p>Alerts are about today. A completed job whose data date is not today in
 * America/Argentina/Buenos_Aires (a replay, a late run of a past date, a date in the future) is not evaluated, because
 * it would compare old data with the alert state of a newer day.
 */
public class AlertEvaluationListener implements JobExecutionListener {

	private static final Logger log = LoggerFactory.getLogger(AlertEvaluationListener.class);

	private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

	private final AlertEvaluator evaluator;

	private final Clock clock;

	public AlertEvaluationListener(AlertEvaluator evaluator, Clock clock) {
		this.evaluator = evaluator;
		this.clock = clock;
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
			return;
		}
		String dataDate = jobExecution.getJobParameters().getString(IngestionService.DATA_DATE_PARAMETER);
		try {
			LocalDate date = LocalDate.parse(dataDate);
			LocalDate today = LocalDate.now(clock.withZone(BUENOS_AIRES));
			if (!date.equals(today)) {
				log.info("alert evaluation skipped for {}: alerts are evaluated only for today ({})", date, today);
				return;
			}
			evaluator.evaluate(date);
		}
		catch (RuntimeException e) {
			log.warn("alert evaluation for {} failed: {}", dataDate, e.getMessage(), e);
		}
	}

}
