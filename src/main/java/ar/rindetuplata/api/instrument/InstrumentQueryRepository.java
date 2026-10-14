package ar.rindetuplata.api.instrument;

import ar.rindetuplata.api.JsonScales;
import ar.rindetuplata.source.macro.MacroVariable;
import ar.rindetuplata.store.InstrumentKind;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The read queries behind the two instrument endpoints. It reads the T4 tables directly with {@code JdbcClient}
 * (no JPA, no domain entities): the latest snapshot (or, for a fund class, the latest quote) per instrument, the
 * snapshots or quotes of a date range, and the macro series of that range.
 */
@Component
public class InstrumentQueryRepository {

	/** Every instrument with its latest rate snapshot and its latest fund quote, in id order. */
	private static final String LATEST = """
			select i.id, i.kind, i.display_name, i.entity_name, i.segment, i.source_id,
			       s.capture_date, s.data_date, s.basis, s.rate, s.client_rate, s.confidence as snapshot_confidence,
			       s.verified_at, s.flags as snapshot_flags, s.invalid as snapshot_invalid,
			       q.quote_date, q.vcp, q.confidence as quote_confidence, q.flags as quote_flags,
			       q.invalid as quote_invalid
			from instrument i
			left join lateral (
				select * from rate_snapshot rs
				where rs.instrument_id = i.id
				order by rs.capture_date desc
				limit 1
			) s on true
			left join lateral (
				select * from fund_quote fq
				where fq.instrument_id = i.id
				order by fq.quote_date desc
				limit 1
			) q on true
			""";

	private final JdbcClient jdbc;

	public InstrumentQueryRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** The identity of one instrument, or empty when there is no such id. */
	public record Header(long id, InstrumentKind kind, String displayName, String entityName, String segment,
			String sourceId) {
	}

	/** One fund quote of a series: the source's own date and the cuotaparte value. */
	public record QuoteRow(LocalDate date, BigDecimal vcp) {
	}

	public List<InstrumentSummary> summaries(InstrumentKind kind) {
		String sql = LATEST + (kind == null ? "" : " where i.kind = :kind") + " order by i.id";
		if (kind == null) {
			return jdbc.sql(sql).query((rs, rowNum) -> summary(rs)).list();
		}
		return jdbc.sql(sql).param("kind", kind.name()).query((rs, rowNum) -> summary(rs)).list();
	}

	public Optional<Header> header(long id) {
		return jdbc.sql("select id, kind, display_name, entity_name, segment, source_id from instrument where id = :id")
				.param("id", id)
				.query((rs, rowNum) -> new Header(
						rs.getLong("id"),
						InstrumentKind.valueOf(rs.getString("kind")),
						rs.getString("display_name"),
						rs.getString("entity_name"),
						rs.getString("segment"),
						rs.getString("source_id")))
				.optional();
	}

	/** The rate snapshots of a range, oldest first, INVALID ones included: the history shows what was stored. */
	public List<SnapshotPoint> snapshots(long instrumentId, LocalDate from, LocalDate to) {
		return jdbc.sql("""
				select capture_date, data_date, basis, rate, client_rate, invalid, flags
				from rate_snapshot
				where instrument_id = :instrumentId and capture_date between :from and :to
				order by capture_date
				""")
				.param("instrumentId", instrumentId)
				.param("from", from)
				.param("to", to)
				.query((rs, rowNum) -> new SnapshotPoint(
						rs.getObject("capture_date", LocalDate.class),
						rs.getObject("data_date", LocalDate.class),
						rs.getString("basis"),
						JsonScales.rate(rs.getObject("rate", BigDecimal.class)),
						JsonScales.rate(rs.getObject("client_rate", BigDecimal.class)),
						rs.getBoolean("invalid"),
						FlagJson.read(rs.getString("flags"))))
				.list();
	}

	/** The valid quotes of a range, oldest first. An INVALID quote is stored for audit but is not part of a yield. */
	public List<QuoteRow> quotes(long instrumentId, LocalDate from, LocalDate to) {
		return jdbc.sql("""
				select quote_date, vcp
				from fund_quote
				where instrument_id = :instrumentId and invalid = false and quote_date between :from and :to
				order by quote_date
				""")
				.param("instrumentId", instrumentId)
				.param("from", from)
				.param("to", to)
				.query((rs, rowNum) -> quote(rs))
				.list();
	}

	/** The last valid quote before the range: the base of the first quote the range returns. */
	public Optional<QuoteRow> quoteBefore(long instrumentId, LocalDate from) {
		return jdbc.sql("""
				select quote_date, vcp
				from fund_quote
				where instrument_id = :instrumentId and invalid = false and quote_date < :from
				order by quote_date desc
				limit 1
				""")
				.param("instrumentId", instrumentId)
				.param("from", from)
				.query((rs, rowNum) -> quote(rs))
				.optional();
	}

	/** The INDEC monthly inflation observations of a range (BCRA variable 27), oldest first, labeled for the API. */
	public List<MonthlyInflationPoint> monthlyInflation(LocalDate from, LocalDate to) {
		return jdbc.sql("""
				select observation_date, value, confidence, source_id
				from macro_observation
				where variable = :variable and observation_date between :from and :to
				order by observation_date, source_id
				""")
				.param("variable", MacroVariable.MONTHLY_INFLATION.name())
				.param("from", from)
				.param("to", to)
				.query((rs, rowNum) -> new MonthlyInflationPoint(
						InstrumentService.INDEC_CPI_MONTHLY,
						rs.getObject("observation_date", LocalDate.class),
						JsonScales.rate(rs.getObject("value", BigDecimal.class)),
						rs.getString("confidence"),
						rs.getString("source_id")))
				.list();
	}

	private static QuoteRow quote(ResultSet rs) throws SQLException {
		return new QuoteRow(rs.getObject("quote_date", LocalDate.class), rs.getObject("vcp", BigDecimal.class));
	}

	private static InstrumentSummary summary(ResultSet rs) throws SQLException {
		String id = Long.toString(rs.getLong("id"));
		String kind = rs.getString("kind");
		String displayName = rs.getString("display_name");
		String entityName = rs.getString("entity_name");
		String segment = rs.getString("segment");
		String sourceId = rs.getString("source_id");
		LocalDate captureDate = rs.getObject("capture_date", LocalDate.class);
		LocalDate quoteDate = rs.getObject("quote_date", LocalDate.class);
		if (quoteDate != null) {
			// A fund class: its latest quote is its datum, and it has no rate snapshot.
			return new InstrumentSummary(id, kind, displayName, entityName, segment, sourceId, null, null, null, null,
					quoteDate, rs.getString("quote_confidence"), null, rs.getBoolean("quote_invalid"),
					FlagJson.read(rs.getString("quote_flags")), quoteDate,
					JsonScales.money(rs.getObject("vcp", BigDecimal.class)));
		}
		return new InstrumentSummary(id, kind, displayName, entityName, segment, sourceId,
				JsonScales.rate(rs.getObject("rate", BigDecimal.class)),
				rs.getString("basis"),
				JsonScales.rate(rs.getObject("client_rate", BigDecimal.class)),
				captureDate,
				rs.getObject("data_date", LocalDate.class),
				rs.getString("snapshot_confidence"),
				rs.getObject("verified_at", LocalDate.class),
				rs.getBoolean("snapshot_invalid"),
				FlagJson.read(rs.getString("snapshot_flags")),
				null,
				null);
	}
}
