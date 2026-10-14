package ar.rindetuplata.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A nominal annual rate (TNA) stored as a fraction exactly as the source gives it (20% is {@code 0.20}).
 * Argentine day count: 365 days a year.
 */
public record AnnualRate(BigDecimal fraction) {

    public static final int DAY_COUNT = 365;
    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(DAY_COUNT);

    public AnnualRate {
        Objects.requireNonNull(fraction, "fraction");
        if (fraction.signum() < 0) {
            throw new IllegalArgumentException("Rate cannot be negative: " + fraction);
        }
    }

    public static AnnualRate ofPercent(String percent) {
        return new AnnualRate(new BigDecimal(percent).movePointLeft(2));
    }

    public static AnnualRate ofFraction(String fraction) {
        return new AnnualRate(new BigDecimal(fraction));
    }

    public BigDecimal percent() {
        return fraction.movePointRight(2);
    }

    /** TNA / 365. */
    public BigDecimal dailyRate() {
        return fraction.divide(DAYS_IN_YEAR, Money.MATH);
    }

    /** Simple interest for the term: principal · TNA · days / 365. */
    public Money simpleInterest(Money principal, int days) {
        return principal.times(fraction).times(BigDecimal.valueOf(days)).dividedBy(DAYS_IN_YEAR);
    }

    /** Growth factor of daily compounding over {@code days}: (1 + TNA/365)^days. */
    public BigDecimal dailyCompoundingGrowth(int days) {
        return DecimalMath.pow(BigDecimal.ONE.add(dailyRate()), BigDecimal.valueOf(days), Money.MATH);
    }

    /** TEA when compounding every {@code periodDays} days: (1 + TNA·n/365)^(365/n) − 1. */
    public BigDecimal tea(int periodDays) {
        if (periodDays <= 0) {
            throw new IllegalArgumentException("Compounding period must be positive: " + periodDays);
        }
        BigDecimal n = BigDecimal.valueOf(periodDays);
        BigDecimal periodRate = fraction.multiply(n, Money.MATH).divide(DAYS_IN_YEAR, Money.MATH);
        BigDecimal periodsPerYear = DAYS_IN_YEAR.divide(n, Money.MATH);
        return DecimalMath.pow(BigDecimal.ONE.add(periodRate), periodsPerYear, Money.MATH).subtract(BigDecimal.ONE);
    }
}
