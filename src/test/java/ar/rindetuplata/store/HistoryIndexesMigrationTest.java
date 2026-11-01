package ar.rindetuplata.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * T4a (L33 a): migration V11 adds the date indexes the monthly report's earliest-history lookup and the backfilled
 * history need, and nothing else: the primary keys of both tables start with the instrument, so a bare date
 * predicate or {@code min(date)} had no index.
 */
@Import(StoreTestConfiguration.class)
@SpringBootTest
@Transactional
class HistoryIndexesMigrationTest {

	@Autowired
	JdbcClient jdbc;

	@Test
	void v11CreatesExactlyTheTwoDateIndexes() {
		assertThat(jdbc.sql("select version from flyway_schema_history where version = '11'")
				.query(String.class).list()).containsExactly("11");
		assertThat(jdbc.sql("""
				select indexdef from pg_indexes
				where schemaname = 'public' and indexname in ('rate_snapshot_capture_date_idx', 'fund_quote_quote_date_idx')
				order by indexname
				""").query(String.class).list()).containsExactly(
				"CREATE INDEX fund_quote_quote_date_idx ON public.fund_quote USING btree (quote_date)",
				"CREATE INDEX rate_snapshot_capture_date_idx ON public.rate_snapshot USING btree (capture_date)");
	}

	@Test
	void theEarliestDateLookupsCanUseThem() {
		jdbc.sql("set local enable_seqscan = off").update();

		List<String> snapshotPlan = jdbc.sql("explain select min(capture_date) from rate_snapshot")
				.query(String.class).list();
		List<String> quotePlan = jdbc.sql("explain select min(quote_date) from fund_quote").query(String.class).list();

		assertThat(String.join("\n", snapshotPlan)).contains("rate_snapshot_capture_date_idx");
		assertThat(String.join("\n", quotePlan)).contains("fund_quote_quote_date_idx");
	}
}
