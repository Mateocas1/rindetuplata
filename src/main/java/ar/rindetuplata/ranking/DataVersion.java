package ar.rindetuplata.ranking;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The version of everything the public ranking is computed from (T3a, L22), read from the database on every call so
 * that every writer invalidates it with no event bus and no shared in-memory flag, across restarts and instances.
 *
 * <p>It is one cheap aggregate over what writers leave behind: the {@code source_run} rows (the daily job, the
 * startup ingestion and any backfill record one per source and day, and re-recording a day replaces the row with a
 * later {@code recorded_at}; a backfill (T4a) never replaces a live row, so it also records one row of its own per
 * run, which keeps the version moving when it only fills gaps under days that already had a run), the curated wallet
 * rates and their audit trail (a save or a confirm writes both and publishes the rate snapshot), and the newest instrument id (a first-seen instrument). The ranking tables
 * themselves are not scanned: they grow with every day and fund class, and the contract is that a writer of ranking
 * data records one of the above. The version is read before the ranking is computed, so a cache entry is never
 * labelled newer than the data it holds; a write that lands in between only makes the next request recompute.
 * The insert-only tables are counted, not read by their highest id: two overlapping transactions can commit in the
 * opposite order of their ids, and a count moves on every commit whatever the order.
 */
@Component
public class DataVersion {

	private static final String AGGREGATE = """
			select concat_ws('|',
			       (select count(*) from source_run),
			       (select floor(extract(epoch from max(recorded_at)) * 1000000) from source_run),
			       (select count(*) from curated_wallet_rate),
			       (select floor(extract(epoch from max(updated_at)) * 1000000) from curated_wallet_rate),
			       (select count(*) from curated_wallet_rate_audit),
			       (select count(*) from instrument))
			""";

	private final JdbcClient jdbc;

	public DataVersion(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** An opaque token that changes whenever ranking data was written. */
	public String current() {
		return jdbc.sql(AGGREGATE).query(String.class).single();
	}
}
