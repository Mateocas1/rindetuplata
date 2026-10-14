package ar.rindetuplata.store;

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

	private static final String INSERT = """
			insert into dollar_quote (source_id, capture_date, buy, sell, updated_at, fetched_at, confidence, flags)
			values (:sourceId, :captureDate, :buy, :sell, :updatedAt, :fetchedAt, :confidence, cast(:flags as jsonb))
			on conflict (source_id, capture_date) do update
			set buy = excluded.buy,
			    sell = excluded.sell,
			    updated_at = excluded.updated_at,
			    fetched_at = excluded.fetched_at,
			    confidence = excluded.confidence,
			    flags = excluded.flags
			""";

	private final JdbcClient jdbc;

	public DollarQuoteWriter(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional
	public WriteResult write(List<DollarQuote> quotes, LocalDate captureDate) {
		for (DollarQuote quote : quotes) {
			jdbc.sql(INSERT)
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
		return WriteResult.of(quotes.size());
	}
}
