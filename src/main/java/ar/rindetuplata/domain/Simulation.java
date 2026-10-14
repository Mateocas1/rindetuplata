package ar.rindetuplata.domain;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Result of simulating an instrument with its unconditional rate. A conditional rate, if any, is reported
 * separately as an "up to" outcome.
 */
public record Simulation(Money initial, Money finalAmount, int days, Optional<ConditionalOutcome> conditional)
        implements Evaluation {

    public Money earned() {
        return finalAmount.minus(initial);
    }

    /** Earned / initial over the whole horizon. */
    public BigDecimal nominalReturn() {
        return earned().value().divide(initial.value(), Money.MATH);
    }
}
