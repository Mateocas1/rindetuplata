package ar.rindetuplata.api.instrument;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One cuotaparte quote and the yield it implies against the quote before it. {@code tnaEquivalent} is the
 * daily-compounding TNA-equivalent of {@code FundYield} between the two quotes, or {@code null} when the vcp fell
 * (such a class has no TNA-equivalent and is never shown as a yield). {@code tea} is the effective annual rate of
 * the same two quotes, with the same {@code null} rule.
 */
public record FundQuotePoint(LocalDate quoteDate, BigDecimal vcp, BigDecimal tnaEquivalent, BigDecimal tea) {
}
