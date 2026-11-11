package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.rindetuplata.TestContainers;
import java.math.BigDecimal;
import java.sql.Date;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * V9: every rule stored before the migration becomes a {@code THRESHOLD} rule with its threshold,
 * kind, instrument and match state untouched, and the table then refuses a row whose fields do not fit its type.
 * The schema is migrated to V8 first, the legacy rows are inserted the way V5 allowed them, then V9 is applied.
 */
class AlertRuleMigrationTest {

	@Test
	void existingRulesBecomeThresholdRulesAndKeepEveryValue() {
		try (PostgreSQLContainer postgres = TestContainers.postgres()) {
			postgres.start();
			DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(),
					postgres.getUsername(), postgres.getPassword());
			migrate(dataSource, "8");
			JdbcClient jdbc = JdbcClient.create(dataSource);
			jdbc.sql("insert into app_user (id, email, created_at) values (1, 'legacy@example.com', now())").update();
			jdbc.sql("""
					insert into instrument (id, kind, source_id, natural_key, display_name, first_seen, last_seen)
					values (7, 'WALLET', 'legacy', 'w', 'Legacy wallet', date '2026-10-14', date '2026-10-14')
					""").update();
			jdbc.sql("""
					insert into alert_rule (id, user_id, kind, instrument_id, threshold_rate, active, created_at)
					values (1, 1, null, null, 0.25, true, now()),
					       (2, 1, 'WALLET', 7, 3, false, now())
					""").update();
			jdbc.sql("insert into alert_match (rule_id, instrument_id, matched_on) values (2, 7, date '2026-10-14')")
				.update();
			jdbc.sql("select setval(pg_get_serial_sequence('alert_rule', 'id'), 2)").query(Long.class).single();

			migrate(dataSource, null);

			assertThat(jdbc.sql("select type from alert_rule order by id").query(String.class).list())
				.as("a rule stored before V9 is a THRESHOLD rule")
				.containsExactly("THRESHOLD", "THRESHOLD");
			assertThat(jdbc.sql("select threshold_rate from alert_rule order by id").query(BigDecimal.class).list())
				.usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
				.containsExactly(new BigDecimal("0.25"), new BigDecimal("3"));
			assertThat(jdbc.sql("select kind from alert_rule where id = 2").query(String.class).single())
				.isEqualTo("WALLET");
			assertThat(jdbc.sql("select instrument_id from alert_rule where id = 2").query(Long.class).single())
				.isEqualTo(7L);
			assertThat(jdbc.sql("select active from alert_rule where id = 2").query(Boolean.class).single()).isFalse();
			assertThat(jdbc.sql("select count(*) from alert_match where rule_id = 2").query(Long.class).single())
				.as("the edge-trigger state of a legacy rule survives").isEqualTo(1);

			assertTheTableRefusesRowsThatDoNotFitTheirType(jdbc);
		}
	}

	private static void assertTheTableRefusesRowsThatDoNotFitTheirType(JdbcClient jdbc) {
		assertThat(insert(jdbc, "THRESHOLD", "null", "null", "0.30")).as("THRESHOLD with a threshold").isEqualTo(1);
		assertThat(insert(jdbc, "BEST_CHANGED", "'FUND'", "null", "null")).as("BEST_CHANGED with a kind").isEqualTo(1);
		assertThat(insert(jdbc, "RATE_DROP", "'WALLET'", "7", "null")).as("RATE_DROP on an instrument").isEqualTo(1);
		assertThat(insert(jdbc, "WEEKLY_DIGEST", "null", "null", "null")).as("WEEKLY_DIGEST without filter")
			.isEqualTo(1);

		assertThatThrownBy(() -> insert(jdbc, "THRESHOLD", "null", "null", "null"))
			.as("THRESHOLD needs its threshold").isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insert(jdbc, "THRESHOLD", "null", "null", "3.5"))
			.as("the threshold range of V5 still holds").isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insert(jdbc, "BEST_CHANGED", "null", "null", "0.30"))
			.as("BEST_CHANGED has no threshold").isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insert(jdbc, "BEST_CHANGED", "null", "7", "null"))
			.as("BEST_CHANGED has no instrument").isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insert(jdbc, "RATE_DROP", "'WALLET'", "null", "null"))
			.as("RATE_DROP needs its instrument").isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insert(jdbc, "RATE_DROP", "'WALLET'", "7", "0.30"))
			.as("RATE_DROP has no threshold").isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insert(jdbc, "WEEKLY_DIGEST", "null", "7", "null"))
			.as("WEEKLY_DIGEST has no instrument").isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insert(jdbc, "NOPE", "null", "null", "0.30"))
			.as("an unknown type").isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void theDigestKeyRefusesASecondDigestOfTheSameRuleAndWeek() {
		try (PostgreSQLContainer postgres = TestContainers.postgres()) {
			postgres.start();
			DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(),
					postgres.getUsername(), postgres.getPassword());
			migrate(dataSource, null);
			JdbcClient jdbc = JdbcClient.create(dataSource);
			jdbc.sql("insert into app_user (id, email, created_at) values (1, 'digest@example.com', now())").update();
			jdbc.sql("""
					insert into alert_rule (id, user_id, type, active, created_at)
					values (1, 1, 'WEEKLY_DIGEST', true, now())
					""").update();
			String digest = """
					insert into alert_digest (rule_id, week_start, sent_for, top_key, top_name)
					values (1, :week, date '2026-10-14', 'id:1', 'Wallet')
					""";
			jdbc.sql(digest).param("week", Date.valueOf("2026-10-12")).update();

			assertThatThrownBy(() -> jdbc.sql(digest).param("week", Date.valueOf("2026-10-12")).update())
				.as("one digest per rule and week").isInstanceOf(DataIntegrityViolationException.class);
			assertThat(jdbc.sql(digest).param("week", Date.valueOf("2026-10-19")).update())
				.as("the next week is free").isEqualTo(1);
		}
	}

	private static void migrate(DriverManagerDataSource dataSource, String target) {
		var configuration = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
		if (target != null) {
			configuration.target(target);
		}
		configuration.load().migrate();
	}

	private static int insert(JdbcClient jdbc, String type, String kind, String instrument, String threshold) {
		return jdbc.sql("insert into alert_rule (user_id, type, kind, instrument_id, threshold_rate, active, created_at)"
				+ " values (1, '" + type + "', " + kind + ", " + instrument + ", " + threshold + ", true, now())")
			.update();
	}

}
