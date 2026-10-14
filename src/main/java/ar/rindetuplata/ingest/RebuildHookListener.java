package ar.rindetuplata.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * T4d: after {@code dailyIngestionJob} COMPLETES, POSTs once to the static site's deploy hook
 * ({@code rindetuplata.ingest.rebuild-hook-url}) so the CDN publishes the day's data (S16, S14). An unset or blank
 * URL is a no-op; a FAILED job never rebuilds; a hook that answers an error or times out is logged and never
 * changes the job, because the ingestion already wrote its data and a hook failure is not an ingestion failure.
 */
class RebuildHookListener implements JobExecutionListener {

	private static final Logger log = LoggerFactory.getLogger(RebuildHookListener.class);

	private final RestClient client;

	private final String url;

	RebuildHookListener(RestClient client, String url) {
		this.client = client;
		this.url = url;
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
			return;
		}
		if (url == null || url.isBlank()) {
			log.debug("no rebuild hook url configured: nothing to rebuild");
			return;
		}
		try {
			client.post().uri(url).retrieve().toBodilessEntity();
			log.info("rebuild hook {} called", url);
		}
		catch (RestClientException e) {
			log.warn("rebuild hook {} failed: {}", url, e.getMessage());
		}
	}

}
