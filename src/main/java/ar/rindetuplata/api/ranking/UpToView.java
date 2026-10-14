package ar.rindetuplata.api.ranking;

import java.math.BigDecimal;

/** The best outcome a labeled condition could reach, reported as "up to" and never used for the ranking. */
public record UpToView(String condition, BigDecimal finalAmount) {
}
