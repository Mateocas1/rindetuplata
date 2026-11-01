package ar.rindetuplata.alert;

import ar.rindetuplata.ranking.RankedEntry;
import ar.rindetuplata.ranking.RankedKind;
import ar.rindetuplata.source.wallet.FundClassKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The slice of the day's ranking a BEST_CHANGED or WEEKLY_DIGEST rule looks at (T6c, L32 a): the entries of its kind
 * filter in ranking order, with the classes of one fund folded into the best one exactly as the public ranking
 * groups them, so a change of class inside one fund is never a change of the best option. The input is the alerts'
 * own ranking ({@code rankAll}: 30 days, non-client, every kind, no page cap), sorted by final amount.
 */
final class AlertRanking {

	private AlertRanking() {
	}

	/** The rule's entries in ranking order, one per fund. */
	static List<RankedEntry> scope(AlertRule rule, List<RankedEntry> ranked) {
		Set<String> funds = new HashSet<>();
		List<RankedEntry> scope = new ArrayList<>();
		for (RankedEntry entry : ranked) {
			if (!rule.matches(entry)) {
				continue;
			}
			if (entry.kind() == RankedKind.FUND && !funds.add(FundClassKey.fundOf(entry.name()))) {
				continue;
			}
			scope.add(entry);
		}
		return scope;
	}

	/** What "the same option" means across evaluations: a fund is its fund, anything else its instrument. */
	static String key(RankedEntry entry) {
		return entry.kind() == RankedKind.FUND ? "fund:" + FundClassKey.fundOf(entry.name()) : "id:" + entry.id();
	}

}
