package ar.rindetuplata.alert;

import ar.rindetuplata.ranking.RankedEntry;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static java.time.temporal.TemporalAdjusters.previousOrSame;

/**
 * "Resumen semanal" (T6c, L32 a): at most one digest per rule per ISO week (Monday start). The first evaluation whose
 * data date falls in a week without one sends it; the data date is already an Argentine calendar date, so no zone
 * conversion applies. The {@code alert_digest} primary key (rule, week) is the guarantee: the row and the outbox
 * event are inserted in one transaction, and a second evaluation of that week conflicts on the key and sends nothing.
 * The digest lists the top {@value #DIGEST_SIZE} of the rule's filter in ranking order, and says when its top
 * differs from the previous digest's. A filter that ranks nothing sends nothing and keeps the week free.
 */
@Component
class WeeklyDigestReconciler {

	static final String EVENT_TYPE = "WeeklyDigest";

	static final int DIGEST_SIZE = 5;

	private final JdbcClient jdbc;

	private final AlertOutbox outbox;

	WeeklyDigestReconciler(JdbcClient jdbc, AlertOutbox outbox) {
		this.jdbc = jdbc;
		this.outbox = outbox;
	}

	@Transactional
	public void reconcile(AlertRule rule, List<RankedEntry> ranked, LocalDate dataDate) {
		List<RankedEntry> scope = AlertRanking.scope(rule, ranked);
		if (scope.isEmpty()) {
			return;
		}
		LocalDate week = weekStart(dataDate);
		RankedEntry top = scope.get(0);
		String topKey = AlertRanking.key(top);
		WeeklyDigestEvent.TopChange previous = previousTop(rule.id(), week, topKey);
		int inserted = jdbc.sql("""
				insert into alert_digest (rule_id, week_start, sent_for, top_key, top_name)
				values (:rule, :week, :sentFor, :topKey, :topName)
				on conflict (rule_id, week_start) do nothing
				""")
			.param("rule", rule.id())
			.param("week", week)
			.param("sentFor", dataDate)
			.param("topKey", topKey)
			.param("topName", top.name())
			.update();
		if (inserted == 0) {
			return;
		}
		List<WeeklyDigestEvent.Entry> entries = scope.stream()
			.limit(DIGEST_SIZE)
			.map(entry -> new WeeklyDigestEvent.Entry(entry.id(), entry.name(), entry.kind().name(),
					entry.rateUsed().fraction(), entry.realReturn(), entry.source().displayName(),
					entry.captureDate(), entry.dataDate()))
			.toList();
		outbox.enqueue(EVENT_TYPE, rule.id(), new WeeklyDigestEvent(AlertType.WEEKLY_DIGEST.name(),
				UUID.randomUUID(), rule.id(), rule.email(), rule.kind() == null ? null : rule.kind().name(), week,
				dataDate, entries, previous));
	}

	/** The Monday of the ISO week a date falls in. */
	static LocalDate weekStart(LocalDate date) {
		return date.with(previousOrSame(DayOfWeek.MONDAY));
	}

	/** The latest earlier digest's top when it differs from {@code topKey}; null when there is none or it is the same. */
	private WeeklyDigestEvent.TopChange previousTop(long ruleId, LocalDate week, String topKey) {
		return jdbc.sql("""
				select week_start, top_key, top_name from alert_digest
				where rule_id = :rule and week_start < :week
				order by week_start desc
				limit 1
				""")
			.param("rule", ruleId)
			.param("week", week)
			.query((rs, rowNumber) -> new PreviousDigest(rs.getString("top_key"),
					new WeeklyDigestEvent.TopChange(rs.getString("top_name"), rs.getObject("week_start", LocalDate.class))))
			.optional()
			.filter(previous -> !previous.topKey().equals(topKey))
			.map(PreviousDigest::change)
			.orElse(null);
	}

	private record PreviousDigest(String topKey, WeeklyDigestEvent.TopChange change) {
	}

}
