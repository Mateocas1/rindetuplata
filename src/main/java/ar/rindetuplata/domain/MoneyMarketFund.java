package ar.rindetuplata.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** Money-market fund: daily compounding at an observed TNA-equivalent, no cap and no minimum term. */
public record MoneyMarketFund(String id, String name, AnnualRate tna) implements Instrument {

    public MoneyMarketFund {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(tna, "tna");
    }

    @Override
    public Evaluation evaluate(Money amount, int days) {
        Instrument.requirePositiveInput(amount, days);
        BigDecimal growth = tna.dailyCompoundingGrowth(days);
        return new Simulation(amount, amount.times(growth), days, Optional.empty());
    }
}
