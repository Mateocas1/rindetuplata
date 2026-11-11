package ar.rindetuplata.ingest.health;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reads the newest {@code source_run} that counts as a success. Only {@code LIVE} rows count: a REPLAY from the
 * archive or a BACKFILL writes old history and says nothing about whether today's fetch works. Only {@code OK} and
 * {@code DEGRADED} count: both stored rows from a source that answered (DEGRADED is the composite plazo fijo with one
 * of its sources down, whose rows are still stored and ranked); {@code FAILED} stored nothing and
 * {@code MISSING_IN_ARCHIVE} is a replay outcome. Any source qualifies, so one source answering is enough to be fresh;
 * the per-source failures stay visible in {@code source_run} itself.
 */
class LastIngestionSuccessReader {

	private final JdbcClient jdbc;

	LastIngestionSuccessReader(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Optional<LastIngestionSuccess> newest() {
		return jdbc.sql("""
				select capture_date, recorded_at
				from source_run
				where mode = 'LIVE' and status in ('OK', 'DEGRADED')
				order by capture_date desc, recorded_at desc
				limit 1
				""")
				.query((rs, rowNum) -> new LastIngestionSuccess(
						rs.getObject("capture_date", LocalDate.class),
						rs.getObject("recorded_at", OffsetDateTime.class).toInstant()))
				.optional();
	}
}
