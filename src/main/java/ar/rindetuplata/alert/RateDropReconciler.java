package ar.rindetuplata.alert;

import ar.rindetuplata.ranking.RankedEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Mi banco bajó la tasa": fires when the TNA the ranking used for the rule's instrument is strictly
 * below the one stored by the previous evaluation. The first evaluation stores the baseline and fires nothing; the
 * stored rate follows the ranking on every evaluation (a rise too), so each new drop below the last seen rate fires
 * once; a day on which the instrument is not ranked changes nothing, state included.
 */
@Component
class RateDropReconciler {

	static final String EVENT_TYPE = "RateDropped";

	private final AlertStateRepository state;

	private final AlertOutbox outbox;

	RateDropReconciler(AlertStateRepository state, AlertOutbox outbox) {
		this.state = state;
		this.outbox = outbox;
	}

	@Transactional
	public void reconcile(AlertRule rule, List<RankedEntry> ranked, LocalDate dataDate) {
		String instrumentId = String.valueOf(rule.instrumentId());
		RankedEntry entry = ranked.stream().filter(candidate -> candidate.id().equals(instrumentId)).findFirst()
			.orElse(null);
		if (entry == null) {
			return;
		}
		BigDecimal rate = entry.rateUsed().fraction();
		state.find(rule.id())
			.filter(previous -> previous.rate() != null && rate.compareTo(previous.rate()) < 0)
			.ifPresent(previous -> outbox.enqueue(EVENT_TYPE, rule.id(),
					new RateDroppedEvent(AlertType.RATE_DROP.name(), UUID.randomUUID(), rule.id(), rule.email(),
							entry.id(), entry.name(), entry.kind().name(), previous.rate(), rate,
							previous.observedOn(), entry.captureDate(), entry.dataDate(),
							entry.source().displayName())));
		state.save(rule.id(), "id:" + entry.id(), entry.name(), rate, dataDate);
	}

}
