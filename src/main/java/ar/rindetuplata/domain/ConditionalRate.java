package ar.rindetuplata.domain;

import java.util.List;

/** An alternative rate schedule that applies only if a labeled condition is met (e.g. monthly spending). */
public record ConditionalRate(String condition, List<RateTier> tiers) {

    public ConditionalRate {
        tiers = RateTier.validated(tiers);
    }
}
