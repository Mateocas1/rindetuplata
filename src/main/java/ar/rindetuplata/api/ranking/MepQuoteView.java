package ar.rindetuplata.api.ranking;

import ar.rindetuplata.ranking.SourceView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The dólar MEP quote every {@code breakEvenMep} of the response was computed from (S5, L24 b), once at the top: the
 * latest stored quote at or before the request's {@code asOf}. Prices are ARS per USD at scale 2. {@code buy} is
 * what the market pays for a dollar and {@code sell} what it charges; {@code quoteDate} is the capture date the
 * quote is stored under and {@code updatedAt} the instant the source reports (null when it gave none).
 */
@Schema(description = "The dollar MEP quote every breakEvenMep of the response was computed from: the latest "
		+ "stored quote at or before the request's asOf. Null when no usable quote is stored.")
public record MepQuoteView(
		@Schema(description = "ARS per USD the market pays for a dollar (the price you leave at), scale 2.")
		BigDecimal buy,
		@Schema(description = "ARS per USD the market charges for a dollar (the price you buy at), scale 2.")
		BigDecimal sell,
		SourceView source,
		@Schema(description = "The capture date the quote is stored under.") LocalDate quoteDate,
		@Schema(description = "The instant the source reports for the quote; null when it gave none.")
		Instant updatedAt) {
}
