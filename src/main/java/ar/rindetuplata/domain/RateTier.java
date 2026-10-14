package ar.rindetuplata.domain;

import java.util.List;
import java.util.Objects;

/**
 * A balance bracket: the part of the balance up to {@code ceiling} (cumulative, from zero) earns {@code rate}.
 * A {@code null} ceiling means unbounded.
 */
public record RateTier(Money ceiling, AnnualRate rate) {

    public RateTier {
        Objects.requireNonNull(rate, "rate");
        if (ceiling != null && !ceiling.isPositive()) {
            throw new IllegalArgumentException("ceiling must be positive");
        }
    }

    public static RateTier upTo(Money ceiling, AnnualRate rate) {
        return new RateTier(Objects.requireNonNull(ceiling, "ceiling"), rate);
    }

    public static RateTier above(AnnualRate rate) {
        return new RateTier(null, rate);
    }

    public boolean isUnbounded() {
        return ceiling == null;
    }

    /** Checks that the tiers are non-empty, with strictly ascending ceilings and only the last one unbounded. */
    static List<RateTier> validated(List<RateTier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalArgumentException("At least one tier is required");
        }
        for (int i = 0; i < tiers.size(); i++) {
            RateTier tier = tiers.get(i);
            boolean last = i == tiers.size() - 1;
            if (tier.isUnbounded() && !last) {
                throw new IllegalArgumentException("Only the last tier can be unbounded");
            }
            if (i > 0 && !tier.isUnbounded() && tier.ceiling().compareTo(tiers.get(i - 1).ceiling()) <= 0) {
                throw new IllegalArgumentException("Tier ceilings must be strictly ascending");
            }
        }
        return List.copyOf(tiers);
    }
}
