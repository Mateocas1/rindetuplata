package ar.rindetuplata.api.ranking;

import java.math.BigDecimal;

/**
 * The rate a row was simulated with, as a fraction at scale 6, with its basis (always TNA here) and which stored
 * rate it was (non-client, client, term band, wallet base, fund or APY TNA-equivalent).
 */
public record RateUsedView(BigDecimal fraction, String basis, String kind) {
}
