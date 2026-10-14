package ar.rindetuplata.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Personalized ranking (S15): every instrument is simulated for the user's amount and days, sorted by final
 * amount descending. Ineligible instruments are returned separately with the reason, never dropped.
 */
public record Ranking(Money amount, int days, BigDecimal inflationOverHorizon, List<Entry> ranked,
        List<Rejected> ineligible) {

    public static final Money DEFAULT_AMOUNT = Money.ars("1000000");
    public static final int DEFAULT_DAYS = 30;

    public record Entry(Instrument instrument, Simulation simulation, BigDecimal realReturn) {
    }

    public record Rejected(Instrument instrument, Ineligible ineligible) {
    }

    public static Ranking withDefaults(List<? extends Instrument> instruments, BigDecimal expectedTwelveMonthInflation) {
        return of(instruments, DEFAULT_AMOUNT, DEFAULT_DAYS, expectedTwelveMonthInflation);
    }

    /**
     * @param expectedTwelveMonthInflation REM 12-month expected inflation as a fraction; converted to the
     *                                     {@code days} horizon by compounding
     */
    public static Ranking of(List<? extends Instrument> instruments, Money amount, int days,
            BigDecimal expectedTwelveMonthInflation) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be positive: " + amount.presented());
        }
        if (days < 1) {
            throw new IllegalArgumentException("Days must be positive: " + days);
        }
        BigDecimal inflation = Inflation.overHorizon(expectedTwelveMonthInflation, days);
        List<Entry> ranked = new ArrayList<>();
        List<Rejected> rejected = new ArrayList<>();
        for (Instrument instrument : instruments) {
            switch (instrument.evaluate(amount, days)) {
                case Simulation simulation -> ranked.add(new Entry(instrument, simulation,
                        Inflation.realReturn(simulation.nominalReturn(), inflation)));
                case Ineligible reason -> rejected.add(new Rejected(instrument, reason));
            }
        }
        ranked.sort(Comparator
                .comparing((Entry e) -> e.simulation().finalAmount()).reversed()
                .thenComparing(e -> e.instrument().name()));
        rejected.sort(Comparator.comparing((Rejected r) -> r.instrument().name()));
        return new Ranking(amount, days, inflation, List.copyOf(ranked), List.copyOf(rejected));
    }
}
