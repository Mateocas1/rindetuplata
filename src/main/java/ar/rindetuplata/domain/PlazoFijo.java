package ar.rindetuplata.domain;

import java.util.Objects;
import java.util.Optional;

/** Fixed-term deposit: simple interest for the term (P · TNA · days / 365), minimum term and minimum amount. */
public record PlazoFijo(String id, String name, AnnualRate tna, int minimumDays, Money minimumAmount)
        implements Instrument {

    public static final int DEFAULT_MINIMUM_DAYS = 30;

    public PlazoFijo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(tna, "tna");
        Objects.requireNonNull(minimumAmount, "minimumAmount");
        if (minimumDays < 1) {
            throw new IllegalArgumentException("Minimum days must be positive: " + minimumDays);
        }
    }

    /** A plazo fijo with the standard 30-day minimum term and no minimum amount. */
    public static PlazoFijo of(String id, String name, AnnualRate tna) {
        return new PlazoFijo(id, name, tna, DEFAULT_MINIMUM_DAYS, Money.ars("0"));
    }

    @Override
    public Evaluation evaluate(Money amount, int days) {
        if (days < minimumDays) {
            return new Ineligible(Ineligible.Reason.MINIMUM_TERM,
                    "Minimum term is " + minimumDays + " days, requested " + days);
        }
        if (amount.isLessThan(minimumAmount)) {
            return new Ineligible(Ineligible.Reason.MINIMUM_AMOUNT,
                    "Minimum amount is " + minimumAmount.presented() + " ARS, requested " + amount.presented());
        }
        Money finalAmount = amount.plus(tna.simpleInterest(amount, days));
        return new Simulation(amount, finalAmount, days, Optional.empty());
    }
}
