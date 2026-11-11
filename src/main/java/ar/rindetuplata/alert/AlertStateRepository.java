package ar.rindetuplata.alert;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The {@code alert_state} table of V9: what a BEST_CHANGED or RATE_DROP rule remembers from the
 * previous evaluation, one row per rule. The row moves on every evaluation that has something to compare.
 */
@Component
class AlertStateRepository {

	private final JdbcClient jdbc;

	AlertStateRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Optional<State> find(long ruleId) {
		return jdbc.sql("select subject_key, subject_name, rate, observed_on from alert_state where rule_id = :rule")
			.param("rule", ruleId)
			.query((rs, rowNumber) -> new State(rs.getString("subject_key"), rs.getString("subject_name"),
					rs.getBigDecimal("rate"), rs.getObject("observed_on", LocalDate.class)))
			.optional();
	}

	void save(long ruleId, String subjectKey, String subjectName, BigDecimal rate, LocalDate observedOn) {
		jdbc.sql("""
				insert into alert_state (rule_id, subject_key, subject_name, rate, observed_on)
				values (:rule, :key, :name, :rate, :observedOn)
				on conflict (rule_id) do update
				set subject_key = excluded.subject_key, subject_name = excluded.subject_name,
				    rate = excluded.rate, observed_on = excluded.observed_on
				""")
			.param("rule", ruleId)
			.param("key", subjectKey)
			.param("name", subjectName)
			.param("rate", rate)
			.param("observedOn", observedOn)
			.update();
	}

	/** The remembered subject: its key and display name, the TNA fraction (RATE_DROP only) and the data date. */
	record State(String subjectKey, String subjectName, BigDecimal rate, LocalDate observedOn) {
	}

}
