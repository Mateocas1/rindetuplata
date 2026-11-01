package ar.rindetuplata.report;

import ar.rindetuplata.domain.MonthlyYield.Realized;
import ar.rindetuplata.ranking.RankedKind;
import java.util.Objects;

/**
 * One instrument measured over a month: what it returned in nominal and real terms, whether it beat inflation, how
 * many days of the month the measurement covers and, for a fund, how many sibling classes were folded into it.
 */
public record ReportRow(String id, RankedKind kind, String name, String entity, String sourceId, Realized realized,
		int coveredDays, int totalDays, int foldedClasses) {

	public ReportRow {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(sourceId, "sourceId");
		Objects.requireNonNull(realized, "realized");
	}

	ReportRow withFoldedClasses(int folded) {
		return new ReportRow(id, kind, name, entity, sourceId, realized, coveredDays, totalDays, folded);
	}
}
