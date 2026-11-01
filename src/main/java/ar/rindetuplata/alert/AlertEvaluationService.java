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
 * The evaluation of T6c (S3, S4, L32 a): it ranks the day the ingestion just wrote with the public defaults
 * ($1.000.000 over 30 days, non-client, every kind, as of the job's data date) once, without truncating it to a page,
 * so a rule on an instrument ranked beyond the public cap still sees it, and hands each active rule that ranking
 * according to its {@link AlertType}. Each rule is reconciled in its own transaction; a rule that fails is logged and
 * never stops the others. With no active rule nothing is read and nothing is written.
 */
@Service
public class AlertEvaluationService implements AlertEvaluator {

	private static final Logger log = LoggerFactory.getLogger(AlertEvaluationService.class);

	private final RankingService ranking;

	private final AlertRuleRepository rules;

	private final AlertRuleReconciler thresholds;

	private final BestChangedReconciler bestChanged;

	private final RateDropReconciler rateDrops;

	private final WeeklyDigestReconciler digests;

	public AlertEvaluationService(RankingService ranking, AlertRuleRepository rules, AlertRuleReconciler thresholds,
			BestChangedReconciler bestChanged, RateDropReconciler rateDrops, WeeklyDigestReconciler digests) {
		this.ranking = ranking;
		this.rules = rules;
		this.thresholds = thresholds;
		this.bestChanged = bestChanged;
		this.rateDrops = rateDrops;
		this.digests = digests;
	}

	@Override
	public void evaluate(LocalDate dataDate) {
		List<AlertRule> active = rules.findActive();
		if (active.isEmpty()) {
			log.debug("no active alert rule: nothing to evaluate for {}", dataDate);
			return;
		}
		RankingResult result = ranking.rankAll(new RankingQuery(RankingQuery.DEFAULT_AMOUNT, RankingQuery.DEFAULT_DAYS,
				Set.of(), null, dataDate, RankingQuery.MAX_LIMIT));
		for (AlertRule rule : active) {
			try {
				switch (rule.type()) {
					case THRESHOLD -> thresholds.reconcile(rule, result.ranked(), dataDate);
					case BEST_CHANGED -> bestChanged.reconcile(rule, result.ranked(), dataDate);
					case RATE_DROP -> rateDrops.reconcile(rule, result.ranked(), dataDate);
					case WEEKLY_DIGEST -> digests.reconcile(rule, result.ranked(), dataDate);
				}
			}
			catch (RuntimeException e) {
				log.warn("alert rule {} ({}) failed for {}: {}", rule.id(), rule.type(), dataDate, e.getMessage(), e);
			}
		}
	}

}
