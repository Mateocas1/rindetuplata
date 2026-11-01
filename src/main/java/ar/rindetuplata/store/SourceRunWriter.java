package ar.rindetuplata.store;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records what a source produced for one capture date: status, row count and, when it failed or held duplicates,
 * the failure text. Re-running a date replaces the row of the same source instead of adding one; the backfill
 * (T4a) records its own rows with {@link #recordBackfill} and never replaces a live or replayed one.
 */
@Component
public class SourceRunWriter {

	private static final String INSERT = """
			insert into source_run (capture_date, source_id, mode, status, rows, failure, recorded_at)
			values (:captureDate, :sourceId, :mode, :status, :rows, :failure, :recordedAt)
			on conflict (capture_date, source_id) do update
			set mode = excluded.mode,
			    status = excluded.status,
			    rows = excluded.rows,
			    failure = excluded.failure,
			    recorded_at = excluded.recorded_at
			""";

	/**
	 * The backfill's statement (T4a): one row per source and day with mode BACKFILL. It replaces an earlier BACKFILL
	 * row (a rerun after a failure) and never a LIVE or REPLAY one, which is what the live job recorded for that day.
	 */
	private static final String INSERT_BACKFILL = """
			insert into source_run (capture_date, source_id, mode, status, rows, failure, recorded_at)
			values (:captureDate, :sourceId, 'BACKFILL', :status, :rows, :failure, :recordedAt)
			on conflict (capture_date, source_id) do update
			set status = excluded.status,
			    rows = excluded.rows,
			    failure = excluded.failure,
			    recorded_at = excluded.recorded_at
			where source_run.mode = 'BACKFILL'
			""";

	private final JdbcClient jdbc;

	public SourceRunWriter(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional
	public WriteResult record(LocalDate captureDate, String sourceId, RunMode mode, RunStatus status, int rows,
			String failure) {
		return record(captureDate, sourceId, mode, status, rows, failure, Instant.now());
	}

	@Transactional
	public WriteResult recordBackfill(LocalDate captureDate, String sourceId, RunStatus status, int rows,
			String failure) {
		return recordBackfill(captureDate, sourceId, status, rows, failure, Instant.now());
	}

	/** Records a backfill row; the result is 1 when it was written and 0 when a live or replayed row kept the slot. */
	@Transactional
	public WriteResult recordBackfill(LocalDate captureDate, String sourceId, RunStatus status, int rows,
			String failure, Instant recordedAt) {
		int recorded = jdbc.sql(INSERT_BACKFILL)
				.param("captureDate", captureDate)
				.param("sourceId", sourceId)
				.param("status", status.name())
				.param("rows", rows)
				.param("failure", failure)
				.param("recordedAt", InstantTimes.of(recordedAt))
				.update();
		return WriteResult.of(recorded);
	}

	@Transactional
	public WriteResult record(LocalDate captureDate, String sourceId, RunMode mode, RunStatus status, int rows,
			String failure, Instant recordedAt) {
		jdbc.sql(INSERT)
				.param("captureDate", captureDate)
				.param("sourceId", sourceId)
				.param("mode", mode.name())
				.param("status", status.name())
				.param("rows", rows)
				.param("failure", failure)
				.param("recordedAt", InstantTimes.of(recordedAt))
				.update();
		return WriteResult.of(1);
	}
}
