package ar.rindetuplata.alert;

import ar.rindetuplata.ranking.RankedEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Cambió la mejor opción": fires when the top entry of the rule's filter differs from the top stored by
 * the previous evaluation. The first evaluation stores the baseline and fires nothing; a day on which the filter ranks
 * nothing changes nothing; evaluating the same data again finds the stored top equal and fires nothing. Funds are
 * compared as the public ranking groups them ({@link AlertRanking}). The state and the event are one transaction.
 */
@Component
class BestChangedReconciler {

	static final String EVENT_TYPE = "BestChanged";

	private final AlertStateRepository state;

	private final AlertOutbox outbox;

	BestChangedReconciler(AlertStateRepository state, AlertOutbox outbox) {
		this.state = state;
		this.outbox = outbox;
	}

	@Transactional
	public void reconcile(AlertRule rule, List<RankedEntry> ranked, LocalDate dataDate) {
		List<RankedEntry> scope = AlertRanking.scope(rule, ranked);
		if (scope.isEmpty()) {
			return;
		}
		RankedEntry top = scope.get(0);
		String key = AlertRanking.key(top);
		state.find(rule.id()).filter(previous -> !previous.subjectKey().equals(key)).ifPresent(previous -> {
			outbox.enqueue(EVENT_TYPE, rule.id(), new BestChangedEvent(AlertType.BEST_CHANGED.name(),
					UUID.randomUUID(), rule.id(), rule.email(), rule.kind() == null ? null : rule.kind().name(),
					top.id(), top.name(), top.kind().name(), top.rateUsed().fraction(), top.realReturn(),
					top.captureDate(), top.dataDate(), top.source().displayName(), previous.subjectName(),
					previous.observedOn()));
		});
		state.save(rule.id(), key, top.name(), null, dataDate);
	}

}
