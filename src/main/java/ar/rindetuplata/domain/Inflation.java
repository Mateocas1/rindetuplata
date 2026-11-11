package ar.rindetuplata.domain;

import java.math.BigDecimal;
import java.util.List;

/** Inflation conversions and real return. All rates are fractions (2% is {@code 0.02}). */
public final class Inflation {

    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(AnnualRate.DAY_COUNT);

    private static final BigDecimal MONTHS_IN_YEAR = BigDecimal.valueOf(12);

    private Inflation() {
    }

    /**
     * Converts an expected 12-month inflation (BCRA REM) to a horizon of {@code days} by compounding:
     * (1 + r)^(days/365) − 1. Never a linear division.
     */
    public static BigDecimal overHorizon(BigDecimal twelveMonthRate, int days) {
        if (days < 0) {
            throw new IllegalArgumentException("days must not be negative");
        }
        BigDecimal exponent = BigDecimal.valueOf(days).divide(DAYS_IN_YEAR, Money.MATH);
        return DecimalMath.pow(BigDecimal.ONE.add(twelveMonthRate), exponent, Money.MATH).subtract(BigDecimal.ONE);
    }

    /** Annualizes one monthly INDEC rate by compounding twelve months: (1 + m)^12 − 1. Never {@code m · 12}. */
    public static BigDecimal annualized(BigDecimal monthlyRate) {
        BigDecimal factor = BigDecimal.ONE.add(monthlyRate);
        if (factor.signum() <= 0) {
            throw new IllegalArgumentException("Monthly inflation must be greater than -100%: " + monthlyRate);
        }
        return DecimalMath.pow(factor, MONTHS_IN_YEAR, Money.MATH).subtract(BigDecimal.ONE);
    }

    /** Cumulative inflation of consecutive monthly INDEC rates: ∏(1 + m) − 1. */
    public static BigDecimal cumulative(List<BigDecimal> monthlyRates) {
        BigDecimal factor = BigDecimal.ONE;
        for (BigDecimal monthly : monthlyRates) {
            factor = factor.multiply(BigDecimal.ONE.add(monthly), Money.MATH);
        }
        return factor.subtract(BigDecimal.ONE);
    }

    /** Real return = (1 + nominal) / (1 + inflation) − 1, both over the same horizon. */
    public static BigDecimal realReturn(BigDecimal nominalReturn, BigDecimal inflation) {
        BigDecimal inflationFactor = BigDecimal.ONE.add(inflation);
        if (inflationFactor.signum() <= 0) {
            throw new IllegalArgumentException("Inflation must be greater than -100%: " + inflation);
        }
        return BigDecimal.ONE.add(nominalReturn).divide(inflationFactor, Money.MATH).subtract(BigDecimal.ONE);
    }

    /**
     * Pesos the investment earns above (positive) or below (negative) the expected inflation over the horizon, in
     * today's pesos: {@code finalAmount / (1 + inflation) − amount}. The division uses {@link Money#MATH}; the
     * result is rounded once, to centavos ({@link Money#PRESENTED_SCALE}, {@link Money#PRESENTED_ROUNDING}).
     */
    public static BigDecimal realEarned(Money amount, Money finalAmount, BigDecimal inflation) {
        BigDecimal inflationFactor = BigDecimal.ONE.add(inflation);
        if (inflationFactor.signum() <= 0) {
            throw new IllegalArgumentException("Inflation must be greater than -100%: " + inflation);
        }
        return finalAmount.value().divide(inflationFactor, Money.MATH).subtract(amount.value(), Money.MATH)
                .setScale(Money.PRESENTED_SCALE, Money.PRESENTED_ROUNDING);
    }
}
