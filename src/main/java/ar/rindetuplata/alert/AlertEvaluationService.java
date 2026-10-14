package ar.rindetuplata.alert;

import ar.rindetuplata.ranking.RankingQuery;
import ar.rindetuplata.ranking.RankingResult;
import ar.rindetuplata.ranking.RankingService;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The evaluation of T6c (S3, S4): it ranks the day the ingestion just wrote with the public defaults ($1.000.000
 * over 30 days, non-client, every kind, as of the job's data date, the maximum page) and hands each active rule its
 * slice of that ranking. Each rule is reconciled in its own transaction by {@link AlertRuleReconciler}; with no
 * active rule nothing is read and nothing is written.
 */
@Service
public class AlertEvaluationService implements AlertEvaluator {

	private static final Logger log = LoggerFactory.getLogger(AlertEvaluationService.class);

	private final RankingService ranking;

	private final AlertRuleRepository rules;

	private final AlertRuleReconciler reconciler;

	public AlertEvaluationService(RankingService ranking, AlertRuleRepository rules, AlertRuleReconciler reconciler) {
		this.ranking = ranking;
		this.rules = rules;
		this.reconciler = reconciler;
	}

	@Override
	public void evaluate(LocalDate dataDate) {
		List<AlertRule> active = rules.findActive();
		if (active.isEmpty()) {
			log.debug("no active alert rule: nothing to evaluate for {}", dataDate);
			return;
		}
		RankingResult result = ranking.rank(new RankingQuery(RankingQuery.DEFAULT_AMOUNT, RankingQuery.DEFAULT_DAYS,
				Set.of(), null, dataDate, RankingQuery.MAX_LIMIT));
		for (AlertRule rule : active) {
			reconciler.reconcile(rule, result.ranked(), dataDate);
		}
	}

}
