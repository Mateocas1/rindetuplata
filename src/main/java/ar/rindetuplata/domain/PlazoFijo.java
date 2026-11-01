package ar.rindetuplata.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Fixed-term deposit with a minimum term and a minimum amount. Each term pays simple interest
 * (P · TNA · term / 365). A horizon of two or more minimum terms is simulated renewing term by term at the same TNA
 * (see {@link #evaluate}).
 */
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

    /**
     * Below two minimum terms the horizon is one term of simple interest. From {@code days >= 2 * minimumDays} it is
     * split into {@code n = days / minimumDays} consecutive terms, each {@code minimumDays} long except the last, which
     * absorbs the remainder ({@code minimumDays + days % minimumDays}); the final amount is
     * {@code amount · Π(1 + TNA · term / 365)} at the same TNA (constant-rate assumption, like the wallets).
     */
    @Override
    public Evaluation evaluate(Money amount, int days) {
        Instrument.requirePositiveInput(amount, days);
        if (days < minimumDays) {
            return new Ineligible(Ineligible.Reason.MINIMUM_TERM,
                    "Minimum term is " + minimumDays + " days, requested " + days);
        }
        if (amount.isLessThan(minimumAmount)) {
            return new Ineligible(Ineligible.Reason.MINIMUM_AMOUNT,
                    "Minimum amount is " + minimumAmount.presented() + " ARS, requested " + amount.presented());
        }
        Money finalAmount = amount;
        int terms = days >= 2 * minimumDays ? days / minimumDays : 1;
        for (int term = 1; term <= terms; term++) {
            int termDays = term < terms ? minimumDays : days - minimumDays * (terms - 1);
            finalAmount = finalAmount.plus(tna.simpleInterest(finalAmount, termDays));
        }
        return new Simulation(amount, finalAmount, days, Optional.empty());
    }
}
