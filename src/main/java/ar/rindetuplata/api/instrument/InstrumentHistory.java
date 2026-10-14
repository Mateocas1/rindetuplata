package ar.rindetuplata.api.instrument;

import java.time.LocalDate;
import java.util.List;

/**
 * The history of one instrument over a closed date range: its rate snapshots per capture date, or its fund quotes
 * with the implied TNA-equivalent, plus the INDEC monthly inflation observations of the same range. The instrument
 * carries its source id, so every row of the response is attributable (S11).
 */
public record InstrumentHistory(
		String id,
		String kind,
		String displayName,
		String entityName,
		String segment,
		String sourceId,
		LocalDate from,
		LocalDate to,
		List<SnapshotPoint> points,
		List<FundQuotePoint> quotes,
		List<MonthlyInflationPoint> monthlyInflation) {
}
