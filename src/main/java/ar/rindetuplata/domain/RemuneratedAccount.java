package ar.rindetuplata.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Remunerated account or wallet. Interest accrues daily on the balance bracket by bracket and is credited to
 * the balance each day, so interest credited above a cap earns nothing. The simulation uses the unconditional
 * {@code tiers}; {@code conditionalRates} are reported as the best "up to" outcome.
 */
public record RemuneratedAccount(String id, String name, List<RateTier> tiers, List<ConditionalRate> conditionalRates)
        implements Instrument {

    public RemuneratedAccount {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        tiers = RateTier.validated(tiers);
        conditionalRates = List.copyOf(conditionalRates);
    }

    /** Balance up to {@code cap} earns {@code rate}; the excess earns nothing. */
    public static RemuneratedAccount capped(String id, String name, AnnualRate rate, Money cap) {
        return capped(id, name, rate, cap, AnnualRate.ofPercent("0"));
    }

    /** Balance up to {@code cap} earns {@code rate}; the excess earns {@code excessRate}. */
    public static RemuneratedAccount capped(String id, String name, AnnualRate rate, Money cap, AnnualRate excessRate) {
        return new RemuneratedAccount(id, name,
                List.of(RateTier.upTo(cap, rate), RateTier.above(excessRate)), List.of());
    }

    @Override
    public Evaluation evaluate(Money amount, int days) {
        Money finalAmount = accrue(tiers, amount, days);
        Optional<ConditionalOutcome> upTo = conditionalRates.stream()
                .map(c -> new ConditionalOutcome(c.condition(), accrue(c.tiers(), amount, days)))
                .max(Comparator.comparing(ConditionalOutcome::finalAmount));
        return new Simulation(amount, finalAmount, days, upTo);
    }

    private static Money accrue(List<RateTier> tiers, Money start, int days) {
        Money balance = start;
        for (int day = 0; day < days; day++) {
            balance = balance.plus(dailyInterest(tiers, balance));
        }
        return balance;
    }

    private static Money dailyInterest(List<RateTier> tiers, Money balance) {
        Money interest = Money.ars("0");
        Money lower = Money.ars("0");
        for (RateTier tier : tiers) {
            Money upper = tier.isUnbounded() || balance.compareTo(tier.ceiling()) < 0 ? balance : tier.ceiling();
            if (upper.compareTo(lower) > 0) {
                interest = interest.plus(upper.minus(lower).times(tier.rate().dailyRate()));
                lower = upper;
            }
        }
        return interest;
    }
}
