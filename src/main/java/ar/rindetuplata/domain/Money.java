package ar.rindetuplata.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount of Argentine pesos. Internal math keeps {@link #MATH} precision; rounding to centavos
 * ({@link RoundingMode#HALF_EVEN}) happens only in {@link #presented()}.
 */
public record Money(BigDecimal value) implements Comparable<Money> {

    public static final MathContext MATH = MathContext.DECIMAL128;
    public static final int PRESENTED_SCALE = 2;
    public static final RoundingMode PRESENTED_ROUNDING = RoundingMode.HALF_EVEN;

    public Money {
        Objects.requireNonNull(value, "value");
    }

    public static Money ars(BigDecimal value) {
        return new Money(value);
    }

    public static Money ars(String value) {
        return new Money(new BigDecimal(value));
    }

    public Money plus(Money other) {
        return new Money(value.add(other.value, MATH));
    }

    public Money minus(Money other) {
        return new Money(value.subtract(other.value, MATH));
    }

    public Money times(BigDecimal factor) {
        return new Money(value.multiply(factor, MATH));
    }

    public Money dividedBy(BigDecimal divisor) {
        return new Money(value.divide(divisor, MATH));
    }

    /** The amount rounded to centavos, for display only. */
    public BigDecimal presented() {
        return value.setScale(PRESENTED_SCALE, PRESENTED_ROUNDING);
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    public boolean isPositive() {
        return value.signum() > 0;
    }

    @Override
    public int compareTo(Money other) {
        return value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Money money && value.compareTo(money.value) == 0;
    }

    @Override
    public int hashCode() {
        return value.signum() == 0 ? 0 : value.stripTrailingZeros().hashCode();
    }
}
