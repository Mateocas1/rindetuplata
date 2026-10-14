package ar.rindetuplata.ranking;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.ValidationFlag;
import ar.rindetuplata.source.macro.BcraMonetariasClient;
import ar.rindetuplata.source.macro.MacroVariable;
import ar.rindetuplata.store.InstrumentKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Read queries over the T4 tables (T5 design notes). Kinds never share a capture date: for every kind and source,
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
			rates.addAll(jdbc.sql("""
					select i.id as instrument_id, i.source_id as source_id, i.display_name as display_name,
					       i.entity_name as entity_name, s.capture_date as capture_date, s.data_date as data_date,
					       s.rate as rate, s.client_rate as client_rate, s.minimum_amount as minimum_amount,
					       s.details as details, s.confidence as confidence, s.verified_at as verified_at,
					       s.flags as flags, s.invalid as invalid
					from rate_snapshot s
					join instrument i on i.id = s.instrument_id
					where i.kind = :kind and i.source_id = :sourceId and s.capture_date = :captureDate
					order by i.id
					""")
					.param("kind", kind.name())
					.param("sourceId", date.getKey())
					.param("captureDate", date.getValue())
					.query((rs, rowNum) -> new StoredRate(
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
							rs.getBoolean("invalid")))
					.list());
		}
		return rates;
	}

	/** Every fund class whose quotes are at or before {@code asOf}, newest quote first per class. */
	Map<Long, List<StoredQuote>> fundSeries(LocalDate asOf) {
		List<StoredQuote> quotes = jdbc.sql("""
				select i.id as instrument_id, i.source_id as source_id, i.display_name as display_name,
				       i.entity_name as entity_name, f.quote_date as quote_date, f.vcp as vcp,
				       f.confidence as confidence, f.flags as flags, f.invalid as invalid
				from fund_quote f
				join instrument i on i.id = f.instrument_id
				where i.kind = :kind and f.quote_date <= :asOf
				order by i.id, f.quote_date desc
				""")
				.param("kind", InstrumentKind.FUND_CLASS.name())
				.param("asOf", asOf)
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
		Map<Long, List<StoredQuote>> byInstrument = new LinkedHashMap<>();
		for (StoredQuote quote : quotes) {
			byInstrument.computeIfAbsent(quote.instrumentId(), key -> new ArrayList<>()).add(quote);
		}
		return byInstrument;
	}

	/** The latest BCRA REM expected 12-month inflation at or before {@code asOf} (S10). */
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
