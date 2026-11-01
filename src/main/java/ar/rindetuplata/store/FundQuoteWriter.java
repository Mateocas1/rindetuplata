package ar.rindetuplata.store;

import ar.rindetuplata.source.fund.FundQuote;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores cuotaparte quotes of fund classes as a time series keyed by the source's own quote date, so a later run
 * corrects a value instead of duplicating it. A quote without a source date falls back to the capture date: the
 * primary key needs one. Rows flagged INVALID are stored with {@code invalid = true}.
 */
@Component
public class FundQuoteWriter {

	private static final String INSERT_INTO = """
			insert into fund_quote (instrument_id, quote_date, vcp, ccp, patrimonio, horizon, fetched_at, confidence,
			                        flags, invalid)
			values (:instrumentId, :quoteDate, :vcp, :ccp, :patrimonio, :horizon, :fetchedAt, :confidence,
			        cast(:flags as jsonb), :invalid)
			""";

	private static final String INSERT = INSERT_INTO + """
			on conflict (instrument_id, quote_date) do update
			set vcp = excluded.vcp,
			    ccp = excluded.ccp,
			    patrimonio = excluded.patrimonio,
			    horizon = excluded.horizon,
			    fetched_at = excluded.fetched_at,
			    confidence = excluded.confidence,
			    flags = excluded.flags,
			    invalid = excluded.invalid
			""";

	/** The backfill's statement: a quote that is already stored (live data) is left exactly as it is. */
	private static final String INSERT_MISSING = INSERT_INTO + "on conflict (instrument_id, quote_date) do nothing";

	private final JdbcClient jdbc;
	private final InstrumentStore instruments;

	public FundQuoteWriter(JdbcClient jdbc, InstrumentStore instruments) {
		this.jdbc = jdbc;
		this.instruments = instruments;
	}

	/** The natural key of the design notes: the full class name as the source gives it. */
	public static String naturalKey(FundQuote quote) {
		return quote.name() == null ? "" : quote.name();
	}

	@Transactional
	public WriteResult write(List<FundQuote> quotes, LocalDate captureDate) {
		return store(quotes, captureDate, false);
	}

	/**
	 * Insert-only variant for the backfill (T4a, L33 a): a quote whose class and date are already stored is skipped,
	 * so live data always wins. The result counts the rows this call really inserted.
	 */
	@Transactional
	public WriteResult writeMissing(List<FundQuote> quotes, LocalDate captureDate) {
		return store(quotes, captureDate, true);
	}

	private WriteResult store(List<FundQuote> quotes, LocalDate captureDate, boolean insertOnly) {
		String statement = insertOnly ? INSERT_MISSING : INSERT;
		int written = 0;
		Map<String, FundQuote> distinct = new LinkedHashMap<>();
		List<String> duplicates = new ArrayList<>();
		for (FundQuote quote : quotes) {
			String key = naturalKey(quote);
			if (distinct.putIfAbsent(key, quote) != null) {
				duplicates.add(key);
			}
		}
		for (Map.Entry<String, FundQuote> entry : distinct.entrySet()) {
			FundQuote quote = entry.getValue();
			long instrumentId = instruments.upsert(InstrumentKind.FUND_CLASS, quote.source().id(), entry.getKey(),
					quote.name(), quote.fund(), quote.segment() == null ? null : quote.segment().name(), captureDate);
			written += jdbc.sql(statement)
					.param("instrumentId", instrumentId)
					.param("quoteDate", quote.date() == null ? captureDate : quote.date())
					.param("vcp", quote.vcp())
					.param("ccp", quote.ccp())
					.param("patrimonio", quote.patrimonio())
					.param("horizon", quote.horizon())
					.param("fetchedAt", InstantTimes.of(quote.fetchedAt()))
					.param("confidence", quote.confidence().name())
					.param("flags", StoreJson.flags(quote.flags()))
					.param("invalid", quote.isInvalid())
					.update();
		}
		return WriteResult.of(insertOnly ? written : distinct.size(), duplicates);
	}
}
