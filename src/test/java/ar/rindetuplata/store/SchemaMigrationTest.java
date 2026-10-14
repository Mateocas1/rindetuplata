package ar.rindetuplata.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The Flyway schema of T4a: the domain tables of V1 and the Spring Batch metadata of V2 exist. */
@Import(StoreTestConfiguration.class)
@SpringBootTest
class SchemaMigrationTest {

	@Autowired
	DataSource dataSource;

	@Test
	void migrationsCreateDomainAndBatchTables() {
		JdbcClient jdbc = JdbcClient.create(dataSource);

		List<String> migrations = jdbc.sql("select version from flyway_schema_history order by installed_rank")
				.query(String.class)
				.list();
		assertThat(migrations).containsSubsequence("1", "2");

		List<String> tables = jdbc.sql("""
				select table_name from information_schema.tables
				where table_schema = 'public' order by table_name
				""").query(String.class).list();

		assertThat(tables).contains("instrument", "rate_snapshot", "fund_quote", "macro_observation",
				"dollar_quote", "source_run", "batch_job_instance");
	}
}
