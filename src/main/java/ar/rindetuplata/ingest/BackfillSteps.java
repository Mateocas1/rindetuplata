package ar.rindetuplata.ingest;

import ar.rindetuplata.source.SourceException;
import ar.rindetuplata.source.dollar.ArgentinaDatosDollarAdapter;
import ar.rindetuplata.source.dollar.DatedDollarQuote;
import ar.rindetuplata.source.dollar.DollarHistorySource;
import ar.rindetuplata.source.fund.ArgentinaDatosFundAdapter;
import ar.rindetuplata.source.fund.FundHistorySource;
import ar.rindetuplata.source.fund.FundQuote;
import ar.rindetuplata.source.macro.BcraMonetariasClient;
import ar.rindetuplata.source.macro.MacroHistorySource;
import ar.rindetuplata.source.macro.MacroObservation;
import ar.rindetuplata.source.macro.MacroVariable;
import ar.rindetuplata.store.DollarQuoteWriter;
import ar.rindetuplata.store.FundQuoteWriter;
import ar.rindetuplata.store.MacroObservationWriter;
import ar.rindetuplata.store.RunStatus;
import ar.rindetuplata.store.SourceRunWriter;
import ar.rindetuplata.store.WriteResult;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * The body of every step of {@code historyBackfillJob}, one method per data kind. Every write is
 * insert-only, so live data wins and a rerun never changes what is stored, and every source gets one {@code source_run}
 * row per day of the range with mode BACKFILL: {@code rows} is what this run inserted (0 when the day was already
 * stored), a day the source has nothing for is an OK row of 0 that says so, and a {@link SourceException} is the failure
 * of one source and day, recorded as FAILED without stopping the others. Any other exception is a defect and
 * fails its step.
 *
 * <p>A backfill row never replaces a live one, so a backfill that only fills gaps under days the daily job already
 * recorded would leave every {@code source_run} row as it was and the data version, which caches key on, would not
 * move. {@link #summary} therefore records one more row per run under its own source id, dated {@code to}.
 */
@Component
public class BackfillSteps {

	static final String FUNDS_SOURCE = ArgentinaDatosFundAdapter.SOURCE.id();
	static final String DOLLAR_SOURCE = ArgentinaDatosDollarAdapter.SOURCE.id();
	static final String MACRO_SOURCE = BcraMonetariasClient.SOURCE.id();

	/** The pseudo source of the run's own summary row: not a data source, it only keeps the data version moving. */
	static final String SUMMARY_SOURCE = "history-backfill";

	static final String NO_FUND_QUOTES = "the source has no quotes for this date";
	static final String NO_DOLLAR_QUOTE = "the source has no quote for this date";
	static final String INCOMPLETE_DOLLAR_QUOTE = "the source quote for this date has no buy or sell price";

	private final FundHistorySource fundHistory;

	private final DollarHistorySource dollarHistory;

	private final MacroHistorySource macroHistory;

	private final FundQuoteWriter fundWriter;

	private final DollarQuoteWriter dollarWriter;

	private final MacroObservationWriter macroWriter;

	private final SourceRunWriter sourceRunWriter;

	private final BackfillPause pause;

	private final BackfillProperties properties;

	public BackfillSteps(FundHistorySource fundHistory, DollarHistorySource dollarHistory,
			MacroHistorySource macroHistory, FundQuoteWriter fundWriter, DollarQuoteWriter dollarWriter,
			MacroObservationWriter macroWriter, SourceRunWriter sourceRunWriter, BackfillPause pause,
			BackfillProperties properties) {
		this.fundHistory = fundHistory;
		this.dollarHistory = dollarHistory;
		this.macroHistory = macroHistory;
		this.fundWriter = fundWriter;
		this.dollarWriter = dollarWriter;
		this.macroWriter = macroWriter;
		this.sourceRunWriter = sourceRunWriter;
		this.pause = pause;
		this.properties = properties;
	}

	/**
	 * The fund classes of one day from the dated endpoint; answers how many rows this call inserted. {@code afterAnotherCall} waits the polite delay first, so the
	 * calls of a run are spaced out and the first one is not delayed.
	 */
	public int fundsDay(LocalDate day, boolean afterAnotherCall) {
		if (afterAnotherCall) {
			waitPolitely(properties.delay());
		}
		try {
			List<FundQuote> quotes = fundHistory.fetchDay(day);
			if (quotes.isEmpty()) {
				record(day, FUNDS_SOURCE, RunStatus.OK, 0, NO_FUND_QUOTES);
				return 0;
			}
			WriteResult result = fundWriter.writeMissing(quotes, day);
			record(day, FUNDS_SOURCE, RunStatus.OK, result.rows(), result.failureText());
			return result.rows();
		}
		catch (SourceException e) {
			record(day, FUNDS_SOURCE, RunStatus.FAILED, 0, e.getMessage());
			return 0;
		}
	}

	/** The ArgentinaDatos MEP series, read once, stored for the days of {@code from..to} that it has a usable quote for. */
	public long dollar(LocalDate from, LocalDate to) {
		List<DatedDollarQuote> series;
		try {
			series = dollarHistory.fetchSeries();
		}
		catch (SourceException e) {
			forEachDay(from, to, day -> record(day, DOLLAR_SOURCE, RunStatus.FAILED, 0, e.getMessage()));
			return 0;
		}
		Map<LocalDate, DatedDollarQuote> usable = new HashMap<>();
		Map<LocalDate, DatedDollarQuote> incomplete = new HashMap<>();
		for (DatedDollarQuote dated : series) {
			if (dated.date().isBefore(from) || dated.date().isAfter(to)) {
				continue;
			}
			boolean complete = dated.quote().buy() != null && dated.quote().sell() != null;
			(complete ? usable : incomplete).putIfAbsent(dated.date(), dated);
		}
		long[] inserted = { 0 };
		forEachDay(from, to, day -> {
			DatedDollarQuote quote = usable.get(day);
			if (quote == null) {
				record(day, DOLLAR_SOURCE, RunStatus.OK, 0,
						incomplete.containsKey(day) ? INCOMPLETE_DOLLAR_QUOTE : NO_DOLLAR_QUOTE);
				return;
			}
			WriteResult result = dollarWriter.writeMissing(List.of(quote));
			record(day, DOLLAR_SOURCE, RunStatus.OK, result.rows(), null);
			inserted[0] += result.rows();
		});
		return inserted[0];
	}

	/**
	 * Every macro variable by date range, one call per variable. A day is OK when every variable answered, DEGRADED
	 * when some failed and FAILED when all did; {@code rows} is what the answering variables inserted for that day.
	 */
	public long macro(LocalDate from, LocalDate to) {
		Map<LocalDate, Integer> insertedByDay = new HashMap<>();
		Map<MacroVariable, String> failures = new LinkedHashMap<>();
		for (MacroVariable variable : MacroVariable.values()) {
			try {
				Map<LocalDate, List<MacroObservation>> byDay = new LinkedHashMap<>();
				for (MacroObservation observation : macroHistory.fetchRange(variable, from, to)) {
					LocalDate date = observation.date();
					if (date == null || observation.value() == null || date.isBefore(from) || date.isAfter(to)) {
						continue;
					}
					byDay.computeIfAbsent(date, key -> new ArrayList<>()).add(observation);
				}
				byDay.forEach((day, observations) -> insertedByDay.merge(day,
						macroWriter.writeMissing(observations, day).rows(), Integer::sum));
			}
			catch (SourceException e) {
				failures.put(variable, e.getMessage());
			}
		}
		RunStatus status = failures.isEmpty() ? RunStatus.OK
				: failures.size() == MacroVariable.values().length ? RunStatus.FAILED : RunStatus.DEGRADED;
		String failure = failures.isEmpty() ? null : "macro variables that failed: " + describe(failures);
		forEachDay(from, to, day -> record(day, MACRO_SOURCE, status, insertedByDay.getOrDefault(day, 0), failure));
		return insertedByDay.values().stream().mapToLong(Integer::longValue).sum();
	}

	/** The run's own row, dated {@code to}: the rows the whole run inserted, and a record that a backfill ran. */
	public void summary(LocalDate from, LocalDate to, long inserted) {
		record(to, SUMMARY_SOURCE, RunStatus.OK, (int) Math.min(inserted, Integer.MAX_VALUE),
				"history backfill " + from + ".." + to);
	}

	private static String describe(Map<MacroVariable, String> failures) {
		List<String> texts = new ArrayList<>();
		failures.forEach((variable, reason) -> texts.add(variable + " (" + reason + ")"));
		return String.join("; ", texts);
	}

	private static void forEachDay(LocalDate from, LocalDate to, Consumer<LocalDate> action) {
		for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
			action.accept(day);
		}
	}

	private void waitPolitely(Duration delay) {
		try {
			pause.pause(delay);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("the history backfill was interrupted", e);
		}
	}

	private void record(LocalDate day, String sourceId, RunStatus status, int rows, String failure) {
		sourceRunWriter.recordBackfill(day, sourceId, status, rows, failure);
	}
}
