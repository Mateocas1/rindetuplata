package ar.rindetuplata.alert;

import ar.rindetuplata.ranking.RankedEntry;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles one THRESHOLD rule against the day's ranked entries, in one transaction (T6c, S4): every entry above the
 * threshold that has no match yet inserts the {@code alert_match} and one {@code AlertTriggered} outbox row, and
 * every match whose instrument is no longer above (or no longer ranked) is deleted, which re-arms the rule. The
 * match and its event are written together, so a crash can never leave a fired alert with no event to relay.
 */
@Component
public class AlertRuleReconciler {

	static final String EVENT_TYPE = "AlertTriggered";

	private final JdbcClient jdbc;

	private final AlertOutbox outbox;

	AlertRuleReconciler(JdbcClient jdbc, AlertOutbox outbox) {
		this.jdbc = jdbc;
		this.outbox = outbox;
	}

	@Transactional
	public void reconcile(AlertRule rule, List<RankedEntry> ranked, LocalDate dataDate) {
		Map<Long, RankedEntry> above = above(rule, ranked);
		Set<Long> matched = matched(rule);

		for (Long instrumentId : matched) {
			if (!above.containsKey(instrumentId)) {
				jdbc.sql("delete from alert_match where rule_id = :rule and instrument_id = :instrumentId")
					.param("rule", rule.id())
					.param("instrumentId", instrumentId)
					.update();
			}
		}
		for (Map.Entry<Long, RankedEntry> entry : above.entrySet()) {
			if (!matched.contains(entry.getKey())) {
				jdbc.sql("insert into alert_match (rule_id, instrument_id, matched_on) values (:rule, :instrumentId, :matchedOn)")
					.param("rule", rule.id())
					.param("instrumentId", entry.getKey())
					.param("matchedOn", dataDate)
					.update();
				insertEvent(rule, entry.getValue(), dataDate);
			}
		}
	}

	/** The ranked entries the rule may match and that are strictly above its threshold, by instrument id. */
	private static Map<Long, RankedEntry> above(AlertRule rule, List<RankedEntry> ranked) {
		Map<Long, RankedEntry> above = new LinkedHashMap<>();
		for (RankedEntry entry : ranked) {
			if (rule.matches(entry) && entry.rateUsed().fraction().compareTo(rule.thresholdRate()) > 0) {
				above.putIfAbsent(Long.valueOf(entry.id()), entry);
			}
		}
		return above;
	}

	private Set<Long> matched(AlertRule rule) {
		return new LinkedHashSet<>(jdbc.sql("select instrument_id from alert_match where rule_id = :rule order by instrument_id")
			.param("rule", rule.id())
			.query(Long.class)
			.list());
	}

	private void insertEvent(AlertRule rule, RankedEntry entry, LocalDate dataDate) {
		outbox.enqueue(EVENT_TYPE, rule.id(), new AlertTriggered(UUID.randomUUID(), rule.id(), rule.email(), entry.id(),
				entry.name(), entry.kind().name(), entry.rateUsed().fraction(), rule.thresholdRate(),
				entry.captureDate(), entry.dataDate(), entry.source().displayName()));
	}

}
