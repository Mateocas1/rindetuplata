package ar.rindetuplata.ranking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The dólar MEP quote a ranking measured its break-even against (S5, L24 b): the latest stored quote at or before
 * {@code asOf}. {@code quoteDate} is the capture date it is stored under; {@code updatedAt} is the instant the source
 * reports for it, null when the source gave none.
 */
public record MepQuote(SourceView source, BigDecimal buy, BigDecimal sell, LocalDate quoteDate, Instant updatedAt) {

	public MepQuote {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(buy, "buy");
		Objects.requireNonNull(sell, "sell");
		Objects.requireNonNull(quoteDate, "quoteDate");
	}
}
