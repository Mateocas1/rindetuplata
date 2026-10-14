package ar.rindetuplata.api.ranking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The request the ranking answered, echoed so the caller sees the effective defaults (S15, S11). {@code amount} is
 * an ARS amount at scale 2; {@code kinds} and {@code clientOf} are the effective, sorted values.
 */
public record RankingRequestView(BigDecimal amount, int days, List<String> clientOf, List<String> kinds,
		LocalDate asOf, int limit) {
}
