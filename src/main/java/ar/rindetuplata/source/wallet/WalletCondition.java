package ar.rindetuplata.source.wallet;

import java.util.List;

/** An alternative rate schedule that applies only if a labeled condition is met (maps to the domain ConditionalRate). */
public record WalletCondition(String condition, List<WalletTier> tiers) {

    public WalletCondition {
        tiers = List.copyOf(tiers);
    }
}
