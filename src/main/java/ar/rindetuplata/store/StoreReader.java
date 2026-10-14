package ar.rindetuplata.store;

import ar.rindetuplata.source.ValidationFlag;
import ar.rindetuplata.source.macro.MacroVariable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Reads what the writers stored, for tests and for the ingestion job's bookkeeping. It goes through the same public
 * shape the writers use (kind, source and natural key) instead of raw table peeking.
 */
@Component
public class StoreReader {

	private final JdbcClient jdbc;

	public StoreReader(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public record InstrumentRow(long id, InstrumentKind kind, String sourceId, String naturalKey, String displayName,
			String entityName, String segment, LocalDate firstSeen, LocalDate lastSeen) {
	}

	public record SnapshotRow(long instrumentId, LocalDate captureDate, LocalDate dataDate, String basis, BigDecimal rate,
			BigDecimal clientRate, BigDecimal minimumAmount, JsonNode details, String confidence, LocalDate verifiedAt,
			Instant fetchedAt, List<ValidationFlag> flags, boolean invalid) {
	}

	public record FundQuoteRow(long instrumentId, LocalDate quoteDate, BigDecimal vcp, BigDecimal ccp,
			BigDecimal patrimonio, String horizon, Instant fetchedAt, String confidence, List<ValidationFlag> flags,
			boolean invalid) {
	}

	public record MacroRow(String variable, LocalDate observationDate, String sourceId, BigDecimal value,
			Instant fetchedAt, String confidence, List<ValidationFlag> flags) {
	}

	public record DollarRow(String sourceId, LocalDate captureDate, BigDecimal buy, BigDecimal sell, Instant updatedAt,
			Instant fetchedAt, String confidence, List<ValidationFlag> flags) {
	}

	public record SourceRunRow(LocalDate captureDate, String sourceId, String mode, String status, int rows,
			String failure, Instant recordedAt) {
	}

	public Optional<InstrumentRow> instrument(InstrumentKind kind, String sourceId, String naturalKey) {
		return jdbc.sql("""
				select id, kind, source_id, natural_key, display_name, entity_name, segment, first_seen, last_seen
				from instrument where kind = :kind and source_id = :sourceId and natural_key = :naturalKey
				""")
				.param("kind", kind.name())
				.param("sourceId", sourceId)
				.param("naturalKey", naturalKey)
				.query((rs, rowNum) -> new InstrumentRow(
						rs.getLong("id"),
						InstrumentKind.valueOf(rs.getString("kind")),
						rs.getString("source_id"),
						rs.getString("natural_key"),
						rs.getString("display_name"),
						rs.getString("entity_name"),
						rs.getString("segment"),
						rs.getObject("first_seen", LocalDate.class),
						rs.getObject("last_seen", LocalDate.class)))
				.optional();
	}

	public int instrumentCount(InstrumentKind kind) {
		return jdbc.sql("select count(*) from instrument where kind = :kind")
				.param("kind", kind.name())
				.query(Integer.class)
				.single();
	}

	public Optional<SnapshotRow> snapshot(InstrumentKind kind, String sourceId, String naturalKey,
			LocalDate captureDate) {
		return jdbc.sql("""
				select s.instrument_id, s.capture_date, s.data_date, s.basis, s.rate, s.client_rate, s.minimum_amount,
				       s.details, s.confidence, s.verified_at, s.fetched_at, s.flags, s.invalid
				from rate_snapshot s join instrument i on i.id = s.instrument_id
				where i.kind = :kind and i.source_id = :sourceId and i.natural_key = :naturalKey
				  and s.capture_date = :captureDate
				""")
				.param("kind", kind.name())
				.param("sourceId", sourceId)
				.param("naturalKey", naturalKey)
				.param("captureDate", captureDate)
				.query((rs, rowNum) -> new SnapshotRow(
						rs.getLong("instrument_id"),
						rs.getObject("capture_date", LocalDate.class),
						rs.getObject("data_date", LocalDate.class),
						rs.getString("basis"),
						rs.getObject("rate", BigDecimal.class),
						rs.getObject("client_rate", BigDecimal.class),
						rs.getObject("minimum_amount", BigDecimal.class),
						StoreJson.read(rs.getString("details")),
						rs.getString("confidence"),
						rs.getObject("verified_at", LocalDate.class),
						InstantTimes.toInstant(rs.getObject("fetched_at", OffsetDateTime.class)),
						StoreJson.readFlags(rs.getString("flags")),
						rs.getBoolean("invalid")))
				.optional();
	}

	public int snapshotCount(InstrumentKind kind) {
		return jdbc.sql("""
				select count(*) from rate_snapshot s join instrument i on i.id = s.instrument_id where i.kind = :kind
				""")
				.param("kind", kind.name())
				.query(Integer.class)
				.single();
	}

	public Optional<FundQuoteRow> fundQuote(String sourceId, String naturalKey, LocalDate quoteDate) {
		return jdbc.sql("""
				select f.instrument_id, f.quote_date, f.vcp, f.ccp, f.patrimonio, f.horizon, f.fetched_at, f.confidence,
				       f.flags, f.invalid
				from fund_quote f join instrument i on i.id = f.instrument_id
				where i.source_id = :sourceId and i.natural_key = :naturalKey and f.quote_date = :quoteDate
				""")
				.param("sourceId", sourceId)
				.param("naturalKey", naturalKey)
				.param("quoteDate", quoteDate)
				.query((rs, rowNum) -> new FundQuoteRow(
						rs.getLong("instrument_id"),
						rs.getObject("quote_date", LocalDate.class),
						rs.getObject("vcp", BigDecimal.class),
						rs.getObject("ccp", BigDecimal.class),
						rs.getObject("patrimonio", BigDecimal.class),
						rs.getString("horizon"),
						InstantTimes.toInstant(rs.getObject("fetched_at", OffsetDateTime.class)),
						rs.getString("confidence"),
						StoreJson.readFlags(rs.getString("flags")),
						rs.getBoolean("invalid")))
				.optional();
	}

	public int fundQuoteCount() {
		return jdbc.sql("select count(*) from fund_quote").query(Integer.class).single();
	}

	public Optional<MacroRow> macro(MacroVariable variable, LocalDate observationDate, String sourceId) {
		return jdbc.sql("""
				select variable, observation_date, source_id, value, fetched_at, confidence, flags
				from macro_observation
				where variable = :variable and observation_date = :observationDate and source_id = :sourceId
				""")
				.param("variable", variable.name())
				.param("observationDate", observationDate)
				.param("sourceId", sourceId)
				.query((rs, rowNum) -> new MacroRow(
						rs.getString("variable"),
						rs.getObject("observation_date", LocalDate.class),
						rs.getString("source_id"),
						rs.getObject("value", BigDecimal.class),
						InstantTimes.toInstant(rs.getObject("fetched_at", OffsetDateTime.class)),
						rs.getString("confidence"),
						StoreJson.readFlags(rs.getString("flags"))))
				.optional();
	}

	public int macroCount() {
		return jdbc.sql("select count(*) from macro_observation").query(Integer.class).single();
	}

	public Optional<DollarRow> dollar(String sourceId, LocalDate captureDate) {
		return jdbc.sql("""
				select source_id, capture_date, buy, sell, updated_at, fetched_at, confidence, flags
				from dollar_quote where source_id = :sourceId and capture_date = :captureDate
				""")
				.param("sourceId", sourceId)
				.param("captureDate", captureDate)
				.query((rs, rowNum) -> new DollarRow(
						rs.getString("source_id"),
						rs.getObject("capture_date", LocalDate.class),
						rs.getObject("buy", BigDecimal.class),
						rs.getObject("sell", BigDecimal.class),
						InstantTimes.toInstant(rs.getObject("updated_at", OffsetDateTime.class)),
						InstantTimes.toInstant(rs.getObject("fetched_at", OffsetDateTime.class)),
						rs.getString("confidence"),
						StoreJson.readFlags(rs.getString("flags"))))
				.optional();
	}

	public int dollarCount() {
		return jdbc.sql("select count(*) from dollar_quote").query(Integer.class).single();
	}

	public Optional<SourceRunRow> sourceRun(LocalDate captureDate, String sourceId) {
		return jdbc.sql("""
				select capture_date, source_id, mode, status, rows, failure, recorded_at
				from source_run where capture_date = :captureDate and source_id = :sourceId
				""")
				.param("captureDate", captureDate)
				.param("sourceId", sourceId)
				.query((rs, rowNum) -> new SourceRunRow(
						rs.getObject("capture_date", LocalDate.class),
						rs.getString("source_id"),
						rs.getString("mode"),
						rs.getString("status"),
						rs.getInt("rows"),
						rs.getString("failure"),
						InstantTimes.toInstant(rs.getObject("recorded_at", OffsetDateTime.class))))
				.optional();
	}

	public int sourceRunCount(LocalDate captureDate) {
		return jdbc.sql("select count(*) from source_run where capture_date = :captureDate")
				.param("captureDate", captureDate)
				.query(Integer.class)
				.single();
	}
}
