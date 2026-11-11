package ar.rindetuplata.api.ranking;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * When an instrument beats the dólar MEP. You buy dollars at the MEP {@code sell} today and leave them at
 * the MEP {@code buy} at the end of the term, so the instrument wins when the MEP buy price rises less than
 * {@code devaluation = (1 + nominalReturn) * sell / buy - 1} (a fraction at scale 6, so 0.023100 is 2,31%).
 * {@code price} is the break-even MEP buy price, {@code (1 + nominalReturn) * sell}, at scale 2. Both use the quote
 * at the top of the response and the entry's own nominal return over the requested days.
 */
@Schema(description = BreakEvenMepView.DESCRIPTION)
public record BreakEvenMepView(
		@Schema(description = "The highest rise of the MEP buy price, as a fraction at scale 6 (0.023100 is 2.31%), "
				+ "at which the instrument still beats the dollar over the requested days.") BigDecimal devaluation,
		@Schema(description = "The break-even MEP buy price in ARS per USD at scale 2: (1 + nominalReturn) * sell. "
				+ "Above it at the end of the term the dollar wins; below it the instrument wins.") BigDecimal price) {

	static final String DESCRIPTION = "The dollar MEP break-even of the instrument over the requested days. A saver "
			+ "buys dollars at the MEP sell price today and leaves them at the MEP buy price at the end, so the "
			+ "instrument wins when the MEP buy price rises less than devaluation = "
			+ "(1 + nominalReturn) * sell / buy - 1; the spread is part of the answer. Computed from the quote in "
			+ "dollarMep; null (never invented) when no MEP quote is stored.";
}
