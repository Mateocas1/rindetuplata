package ar.rindetuplata.ingest;

import ar.rindetuplata.store.RunMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.batch.core.BatchStatus;

/**
 * What one call to {@link IngestionService#run} did: the status of the job execution, whether the launch was a
 * no-op because the data date was already ingested, and the failure messages of a failed execution (a missing
 * archive manifest, an unexpected step error).
 */
public record IngestionResult(
		LocalDate dataDate,
		RunMode mode,
		BatchStatus status,
		boolean alreadyCompleted,
		Long jobExecutionId,
		List<String> failureMessages) {

	public IngestionResult {
		failureMessages = List.copyOf(failureMessages);
	}

	/** A launch that found a COMPLETED instance for the same data date: nothing ran, nothing was written. */
	public static IngestionResult alreadyCompleted(LocalDate dataDate, RunMode mode) {
		return new IngestionResult(dataDate, mode, BatchStatus.COMPLETED, true, null, List.of());
	}

	public boolean completed() {
		return status == BatchStatus.COMPLETED;
	}
}
