package ar.rindetuplata.report;

import ar.rindetuplata.domain.MonthlyYield;
import ar.rindetuplata.source.macro.BcraMonetariasClient;
import ar.rindetuplata.source.macro.MacroVariable;
import ar.rindetuplata.store.InstrumentKind;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The read queries behind the monthly report (T6a): the INDEC monthly inflation observations, the rate snapshots of
 * plazo fijo, wallets and ARS crypto around one month, and the fund quotes around its two month ends. Every query is
 * bounded by the month, so its cost does not grow with the history stored.
 */
@Component
class MonthlyReportRepository {

	private final JdbcClient jdbc;

	MonthlyReportRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** One stored rate: INVALID snapshots are kept so the report can say they were the only data. */
	record Snapshot(LocalDate date, BigDecimal rate, boolean invalid) {
	}

	/** The identity of an instrument and its snapshots in the window, oldest first. */
	record RateSeries(long id, InstrumentKind kind, String name, String entity, String sourceId,
			List<Snapshot> snapshots) {
	}

	/** A fund class and its valid quotes in the two month-end windows, oldest first. */
	record FundSeries(long id, String name, String entity, String sourceId, List<MonthlyYield.Quote> quotes) {
	}

	/** The INDEC observations by month, newest first; the official BCRA source wins over an aggregator. */
	Map<YearMonth, MonthlyInflation> inflation() {
		return observations(null);
	}

	Optional<MonthlyInflation> inflation(YearMonth month) {
		return Optional.ofNullable(observations(month).get(month));
	}

	private Map<YearMonth, MonthlyInflation> observations(YearMonth only) {
		JdbcClient.StatementSpec statement = jdbc.sql("""
				select observation_date, value, confidence, source_id
				from macro_observation
				where variable = :variable
				""" + (only == null ? "" : "and observation_date between :from and :to\n") + """
				order by observation_date desc, (source_id = :official) desc, source_id
				""")
				.param("variable", MacroVariable.MONTHLY_INFLATION.name())
				.param("official", BcraMonetariasClient.SOURCE.id());
		if (only != null) {
			statement = statement.param("from", only.atDay(1)).param("to", only.atEndOfMonth());
		}
		Map<YearMonth, MonthlyInflation> byMonth = new LinkedHashMap<>();
		statement.query((rs, rowNum) -> new MonthlyInflation(rs.getObject("value", BigDecimal.class),
				rs.getObject("observation_date", LocalDate.class), rs.getString("confidence"),
				rs.getString("source_id")))
				.list()
				.forEach(inflation -> byMonth.putIfAbsent(YearMonth.from(inflation.observationDate()), inflation));
		return byMonth;
	}

	/** The oldest day any rate snapshot or fund quote is stored for, or empty when nothing is stored. */
	Optional<LocalDate> earliestHistory() {
		List<LocalDate> earliest = jdbc.sql("""
				select least((select min(capture_date) from rate_snapshot), (select min(quote_date) from fund_quote))
				""")
				.query((rs, rowNum) -> rs.getObject(1, LocalDate.class))
				.list();
		return earliest.stream().filter(Objects::nonNull).findFirst();
	}

	/**
	 * The snapshots of every plazo fijo bank, wallet and ARS crypto yield from {@code MAX_CAPTURE_GAP_DAYS} before the
	 * month starts (the capture that still covers its first day) to its last day.
	 */
	List<RateSeries> rateSeries(YearMonth month) {
		Map<Long, RateSeries> byId = new LinkedHashMap<>();
		jdbc.sql("""
				select i.id, i.kind, i.display_name, i.entity_name, i.source_id, s.capture_date, s.rate, s.invalid
				from instrument i
				join rate_snapshot s on s.instrument_id = i.id
				where i.kind in (:kinds)
				  and s.capture_date between :from and :to
				  and (i.kind <> :crypto or s.details ->> 'currency' = 'ARS')
				order by i.id, s.capture_date
				""")
				.param("kinds", List.of(InstrumentKind.PLAZO_FIJO_BANK.name(), InstrumentKind.WALLET.name(),
						InstrumentKind.CRYPTO_YIELD.name()))
				.param("crypto", InstrumentKind.CRYPTO_YIELD.name())
				.param("from", month.atDay(1).minusDays(MonthlyYield.MAX_CAPTURE_GAP_DAYS))
				.param("to", month.atEndOfMonth())
				.query((rs, rowNum) -> {
					RateSeries series = byId.computeIfAbsent(rs.getLong("id"), id -> newSeries(rs, id));
					series.snapshots().add(new Snapshot(rs.getObject("capture_date", LocalDate.class),
							rs.getObject("rate", BigDecimal.class), rs.getBoolean("invalid")));
					return rowNum;
				})
				.list();
		return new ArrayList<>(byId.values());
	}

	private static RateSeries newSeries(ResultSet rs, long id) {
		try {
			return new RateSeries(id, InstrumentKind.valueOf(rs.getString("kind")), rs.getString("display_name"),
					rs.getString("entity_name"), rs.getString("source_id"), new ArrayList<>());
		}
		catch (SQLException e) {
			throw new IllegalStateException("Cannot read an instrument row", e);
		}
	}

	/**
	 * Every fund class that has a quote anywhere between the start of the base window and the month end, in id order,
	 * with the valid quotes inside the two windows {@link MonthlyYield#fund} reads. A class with no valid quote in a
	 * window is still returned, so the report can say which window it missed.
	 */
	List<FundSeries> fundSeries(YearMonth month) {
		LocalDate previousEnd = month.minusMonths(1).atEndOfMonth();
		LocalDate end = month.atEndOfMonth();
		Map<Long, FundSeries> byId = new LinkedHashMap<>();
		jdbc.sql("""
				select i.id, i.display_name, i.entity_name, i.source_id, f.quote_date, f.vcp
				from instrument i
				left join fund_quote f
				  on f.instrument_id = i.id and f.invalid = false
				 and (f.quote_date between :baseFrom and :baseTo or f.quote_date between :endFrom and :endTo)
				where i.kind = :kind
				  and exists (select 1 from fund_quote q
				              where q.instrument_id = i.id and q.quote_date between :baseFrom and :endTo)
				order by i.id, f.quote_date
				""")
				.param("kind", InstrumentKind.FUND_CLASS.name())
				.param("baseFrom", previousEnd.minusDays(MonthlyYield.FUND_WINDOW_DAYS))
				.param("baseTo", previousEnd)
				.param("endFrom", end.minusDays(MonthlyYield.FUND_WINDOW_DAYS))
				.param("endTo", end)
				.query((rs, rowNum) -> {
					long id = rs.getLong("id");
					FundSeries series = byId.get(id);
					if (series == null) {
						series = new FundSeries(id, rs.getString("display_name"), rs.getString("entity_name"),
								rs.getString("source_id"), new ArrayList<>());
						byId.put(id, series);
					}
					LocalDate date = rs.getObject("quote_date", LocalDate.class);
					if (date != null) {
						series.quotes().add(new MonthlyYield.Quote(date, rs.getObject("vcp", BigDecimal.class)));
					}
					return id;
				})
				.list();
		return new ArrayList<>(byId.values());
	}
}
