package ar.rindetuplata.store;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records what a source produced for one capture date: status, row count and, when it failed or held duplicates,
 * the failure text. Re-running a date replaces the row of the same source instead of adding one.
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
