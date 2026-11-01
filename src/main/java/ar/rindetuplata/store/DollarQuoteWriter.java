package ar.rindetuplata.store;

import ar.rindetuplata.source.dollar.DatedDollarQuote;
import ar.rindetuplata.source.dollar.DollarQuote;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores the dólar MEP quote of one source and capture date: re-running the date replaces buy/sell instead of
 * adding a row. The source has no data date of its own, so the capture date is the key.
 */
@Component
public class DollarQuoteWriter {

	private static final String INSERT_INTO = """
			insert into dollar_quote (source_id, capture_date, buy, sell, updated_at, fetched_at, confidence, flags)
			values (:sourceId, :captureDate, :buy, :sell, :updatedAt, :fetchedAt, :confidence, cast(:flags as jsonb))
			""";

	private static final String INSERT = INSERT_INTO + """
			on conflict (source_id, capture_date) do update
			set buy = excluded.buy,
			    sell = excluded.sell,
			    updated_at = excluded.updated_at,
			    fetched_at = excluded.fetched_at,
			    confidence = excluded.confidence,
			    flags = excluded.flags
			""";

	/** The backfill's statement: a quote already stored for the source and day is left exactly as it is. */
	private static final String INSERT_MISSING = INSERT_INTO + "on conflict (source_id, capture_date) do nothing";

	private final JdbcClient jdbc;

	public DollarQuoteWriter(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Insert-only variant for the backfill (T4a, L33 a): each quote is stored under its own day and skipped when that
	 * source already has a row for it. The result counts the rows this call really inserted.
	 */
	@Transactional
	public WriteResult writeMissing(List<DatedDollarQuote> quotes) {
		int written = 0;
		for (DatedDollarQuote dated : quotes) {
			written += insert(INSERT_MISSING, dated.quote(), dated.date());
		}
		return WriteResult.of(written);
	}

	@Transactional
	public WriteResult write(List<DollarQuote> quotes, LocalDate captureDate) {
		for (DollarQuote quote : quotes) {
			insert(INSERT, quote, captureDate);
		}
		return WriteResult.of(quotes.size());
	}

	private int insert(String statement, DollarQuote quote, LocalDate captureDate) {
		return jdbc.sql(statement)
				.param("sourceId", quote.source().id())
				.param("captureDate", captureDate)
				.param("buy", quote.buy())
				.param("sell", quote.sell())
				.param("updatedAt", InstantTimes.of(quote.updatedAt()))
				.param("fetchedAt", InstantTimes.of(quote.fetchedAt()))
				.param("confidence", quote.confidence().name())
				.param("flags", StoreJson.flags(quote.flags()))
				.update();
	}
}
