package ar.rindetuplata.ingest;

import ar.rindetuplata.store.RunMode;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the live ingestion once a day, after the T9 capture: today is the day in {@code rindetuplata.ingest.zone},
 * never the machine's day. A failure is logged and never kills the scheduler.
 */
public class IngestionScheduler {

	private static final Logger log = LoggerFactory.getLogger(IngestionScheduler.class);

	private final IngestionService ingestionService;
	private final Clock clock;

	public IngestionScheduler(IngestionService ingestionService, Clock clock) {
		this.ingestionService = ingestionService;
		this.clock = clock;
	}

	@Scheduled(cron = "${rindetuplata.ingest.cron}", zone = "${rindetuplata.ingest.zone}")
	public void runDailyIngestion() {
		LocalDate today = LocalDate.now(clock);
		try {
			IngestionResult result = ingestionService.run(today, RunMode.LIVE);
			log.info("daily ingestion for {} finished: {} (already completed: {})", today, result.status(),
					result.alreadyCompleted());
		}
		catch (RuntimeException e) {
			log.error("daily ingestion for {} failed", today, e);
		}
	}
}
