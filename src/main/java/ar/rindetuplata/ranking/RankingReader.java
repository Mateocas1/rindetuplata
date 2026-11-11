package ar.rindetuplata.ranking;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.ValidationFlag;
import ar.rindetuplata.source.ValidationRules;
import ar.rindetuplata.source.dollar.DolarApiAdapter;
import ar.rindetuplata.source.macro.BcraMonetariasClient;
import ar.rindetuplata.source.macro.MacroVariable;
import ar.rindetuplata.store.InstrumentKind;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Read queries over the tables. Kinds never share a capture date: for every kind and source,
 * its latest {@code capture_date} at or before {@code asOf} is used, so a source that failed today falls back to
 * its last good day and its row shows its own dates.
 */
@Component
class RankingReader {

	private final JdbcClient jdbc;

	RankingReader(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	record StoredRate(long instrumentId, String sourceId, String displayName, String entityName, LocalDate captureDate,
			LocalDate dataDate, BigDecimal rate, BigDecimal clientRate, BigDecimal minimumAmount, JsonNode details,
			Confidence confidence, LocalDate verifiedAt, List<ValidationFlag> flags, boolean invalid) {
	}

	record StoredQuote(long instrumentId, String sourceId, String displayName, String entityName, LocalDate quoteDate,
			BigDecimal vcp, Confidence confidence, List<ValidationFlag> flags, boolean invalid) {
	}

	record StoredInflation(BigDecimal value, LocalDate observationDate) {
	}

	/** The identity of one instrument row: what a targeted read needs to know before it reads the right table. */
	record StoredInstrument(long id, InstrumentKind kind, String sourceId) {
	}

	/** A fund class that has at least one quote in the lookback window, without its quotes. */
	record FundClass(long instrumentId, String displayName) {
	}

	private static final String RATE_COLUMNS = """
			select i.id as instrument_id, i.source_id as source_id, i.display_name as display_name,
			       i.entity_name as entity_name, s.capture_date as capture_date, s.data_date as data_date,
			       s.rate as rate, s.client_rate as client_rate, s.minimum_amount as minimum_amount,
			       s.details as details, s.confidence as confidence, s.verified_at as verified_at,
			       s.flags as flags, s.invalid as invalid
			from rate_snapshot s
			join instrument i on i.id = s.instrument_id
			""";

	private static StoredRate storedRate(ResultSet rs, int rowNum) throws SQLException {
		return new StoredRate(
				rs.getLong("instrument_id"),
				rs.getString("source_id"),
				rs.getString("display_name"),
				rs.getString("entity_name"),
				rs.getObject("capture_date", LocalDate.class),
				rs.getObject("data_date", LocalDate.class),
				rs.getObject("rate", BigDecimal.class),
				rs.getObject("client_rate", BigDecimal.class),
				rs.getObject("minimum_amount", BigDecimal.class),
				RankingJson.read(rs.getString("details")),
				Confidence.valueOf(rs.getString("confidence")),
				rs.getObject("verified_at", LocalDate.class),
				RankingJson.flags(rs.getString("flags")),
				rs.getBoolean("invalid"));
	}

	/** The latest snapshot of every instrument of one kind, per source, at or before {@code asOf}. */
	List<StoredRate> latestRates(InstrumentKind kind, LocalDate asOf) {
		List<Map.Entry<String, LocalDate>> dates = jdbc.sql("""
				select i.source_id as source_id, max(s.capture_date) as capture_date
				from rate_snapshot s
				join instrument i on i.id = s.instrument_id
				where i.kind = :kind and s.capture_date <= :asOf
				group by i.source_id
				order by i.source_id
				""")
				.param("kind", kind.name())
				.param("asOf", asOf)
				.query((rs, rowNum) -> Map.entry(rs.getString("source_id"),
						rs.getObject("capture_date", LocalDate.class)))
				.list();
		List<StoredRate> rates = new ArrayList<>();
		for (Map.Entry<String, LocalDate> date : dates) {
			rates.addAll(jdbc.sql(RATE_COLUMNS + """
					where i.kind = :kind and i.source_id = :sourceId and s.capture_date = :captureDate
					order by i.id
					""")
					.param("kind", kind.name())
					.param("sourceId", date.getKey())
					.param("captureDate", date.getValue())
					.query(RankingReader::storedRate)
					.list());
		}
		return rates;
	}

	/** The identity of one instrument, or empty when no instrument carries the id. */
	Optional<StoredInstrument> instrument(long instrumentId) {
		return jdbc.sql("select id, kind, source_id from instrument where id = :id")
				.param("id", instrumentId)
				.query((rs, rowNum) -> new StoredInstrument(rs.getLong("id"),
						InstrumentKind.valueOf(rs.getString("kind")), rs.getString("source_id")))
				.optional();
	}

	/**
	 * The one snapshot {@link #latestRates} holds for this instrument: its row on the latest capture date of its kind
	 * and source at or before {@code asOf}, or empty when the instrument is not in that day's response.
	 */
	Optional<StoredRate> latestRate(long instrumentId, InstrumentKind kind, LocalDate asOf) {
		return jdbc.sql(RATE_COLUMNS + """
				where i.id = :id and i.kind = :kind and s.capture_date = (
				    select max(s2.capture_date)
				    from rate_snapshot s2
				    join instrument i2 on i2.id = s2.instrument_id
				    where i2.kind = :kind and i2.source_id = i.source_id and s2.capture_date <= :asOf)
				""")
				.param("id", instrumentId)
				.param("kind", kind.name())
				.param("asOf", asOf)
				.query(RankingReader::storedRate)
				.optional();
	}

	/**
	 * The recent quotes of every fund class, newest first per class: those dated in
	 * {@code [asOf - FUND_QUOTE_LOOKBACK_DAYS, asOf]}. {@link FundEvaluator} needs the latest valid quote (stale when
	 * older than {@code FUND_STALE_DAYS}), a base at least {@code FUND_YIELD_WINDOW_DAYS} before it, and a margin of
	 * the same size for the weekends and holidays that can sit between the two, so 21 days reach every quote it can
	 * pick. The bound keeps the read independent of how much history is stored; a class whose newest quote is older
	 * than the bound is absent, the same as one that was never ingested.
	 */
	Map<Long, List<StoredQuote>> fundSeries(LocalDate asOf) {
		Map<Long, List<StoredQuote>> byInstrument = new LinkedHashMap<>();
		for (StoredQuote quote : fundQuotes(asOf, null)) {
			byInstrument.computeIfAbsent(quote.instrumentId(), key -> new ArrayList<>()).add(quote);
		}
		return byInstrument;
	}

	/** The series {@link #fundSeries} holds for one class (newest first), empty when it has no quote in the window. */
	List<StoredQuote> fundSeries(long instrumentId, LocalDate asOf) {
		return fundQuotes(asOf, instrumentId);
	}

	/**
	 * Every class {@link #fundSeries} would hold, by instrument id and without the quotes: a targeted read uses it to
	 * tell which class a mapped wallet resolves to without reading the series of all of them.
	 */
	List<FundClass> fundClasses(LocalDate asOf) {
		return jdbc.sql("""
				select i.id as instrument_id, i.display_name as display_name
				from instrument i
				where i.kind = :kind
				  and exists (select 1 from fund_quote f
				              where f.instrument_id = i.id and f.quote_date between :from and :asOf)
				order by i.id
				""")
				.param("kind", InstrumentKind.FUND_CLASS.name())
				.param("from", asOf.minusDays(ValidationRules.FUND_QUOTE_LOOKBACK_DAYS))
				.param("asOf", asOf)
				.query((rs, rowNum) -> new FundClass(rs.getLong("instrument_id"), rs.getString("display_name")))
				.list();
	}

	private List<StoredQuote> fundQuotes(LocalDate asOf, Long instrumentId) {
		JdbcClient.StatementSpec statement = jdbc.sql("""
				select i.id as instrument_id, i.source_id as source_id, i.display_name as display_name,
				       i.entity_name as entity_name, f.quote_date as quote_date, f.vcp as vcp,
				       f.confidence as confidence, f.flags as flags, f.invalid as invalid
				from fund_quote f
				join instrument i on i.id = f.instrument_id
				where i.kind = :kind and f.quote_date between :from and :asOf
				""" + (instrumentId == null ? "" : "and i.id = :id\n") + """
				order by i.id, f.quote_date desc
				""")
				.param("kind", InstrumentKind.FUND_CLASS.name())
				.param("from", asOf.minusDays(ValidationRules.FUND_QUOTE_LOOKBACK_DAYS))
				.param("asOf", asOf);
		if (instrumentId != null) {
			statement = statement.param("id", instrumentId);
		}
		return statement
				.query((rs, rowNum) -> new StoredQuote(
						rs.getLong("instrument_id"),
						rs.getString("source_id"),
						rs.getString("display_name"),
						rs.getString("entity_name"),
						rs.getObject("quote_date", LocalDate.class),
						rs.getObject("vcp", BigDecimal.class),
						Confidence.valueOf(rs.getString("confidence")),
						RankingJson.flags(rs.getString("flags")),
						rs.getBoolean("invalid")))
				.list();
	}

	/**
	 * The latest usable dólar MEP quote at or before {@code asOf}, whatever the source: the
	 * newest capture date, and on the same date DolarApi (the live capture) before the ArgentinaDatos history, then the
	 * source id so the answer is deterministic. A quote with a non-positive price is skipped, since the break-even
	 * divides by it.
	 */
	Optional<MepQuote> latestDollarMep(LocalDate asOf) {
		return jdbc.sql("""
				select source_id, capture_date, buy, sell, updated_at
				from dollar_quote
				where capture_date <= :asOf and buy > 0 and sell > 0
				order by capture_date desc, (source_id = :preferred) desc, source_id
				limit 1
				""")
				.param("asOf", asOf)
				.param("preferred", DolarApiAdapter.SOURCE.id())
				.query((rs, rowNum) -> {
					OffsetDateTime updatedAt = rs.getObject("updated_at", OffsetDateTime.class);
					return new MepQuote(SourceRegistry.of(rs.getString("source_id")),
							rs.getObject("buy", BigDecimal.class), rs.getObject("sell", BigDecimal.class),
							rs.getObject("capture_date", LocalDate.class),
							updatedAt == null ? null : updatedAt.toInstant());
				})
				.optional();
	}

	/** The latest BCRA REM expected 12-month inflation at or before {@code asOf}. */
	Optional<StoredInflation> latestExpectedInflation(LocalDate asOf) {
		return jdbc.sql("""
				select value, observation_date
				from macro_observation
				where variable = :variable and source_id = :sourceId and observation_date <= :asOf
				order by observation_date desc
				limit 1
				""")
				.param("variable", MacroVariable.EXPECTED_INFLATION_12M.name())
				.param("sourceId", BcraMonetariasClient.SOURCE.id())
				.param("asOf", asOf)
				.query((rs, rowNum) -> new StoredInflation(rs.getObject("value", BigDecimal.class),
						rs.getObject("observation_date", LocalDate.class)))
				.optional();
	}
}
