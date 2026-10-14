package ar.rindetuplata.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Yield of a money-market fund class observed from two cuotaparte (vcp) quotes of the same class.
 * Money-market funds accrue every calendar day, so a Friday-to-Monday quote gap is three days.
 *
 * <p>From {@code (fromDate, fromVcp)} and {@code (toDate, toVcp)} with {@code toDate > fromDate} and
 * {@code n = toDate − fromDate} calendar days:
 * <ul>
 *   <li>{@link #periodReturn()} = {@code toVcp / fromVcp − 1};</li>
 *   <li>{@link #dailyRate()} = {@code (toVcp / fromVcp)^(1/n) − 1};</li>
 *   <li>{@link #tna()} = {@code daily · 365}, the TNA-equivalent for daily compounding, ready for
 *       {@link MoneyMarketFund};</li>
 *   <li>{@link #tea()} = {@code (toVcp / fromVcp)^(365/n) − 1}.</li>
 * </ul>
 *
 * <p>All math uses {@link Money#MATH}; no value is rounded for display here. A fund that lost value
 * yields a negative period return, daily rate and TEA, and has no {@link #tna()} equivalent:
 * {@link AnnualRate} only represents non-negative source rates, so {@code tna()} throws for it and
 * such a class must not be simulated as a {@link MoneyMarketFund}.
 */
public record FundYield(LocalDate fromDate, BigDecimal fromVcp, LocalDate toDate, BigDecimal toVcp) {

    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(AnnualRate.DAY_COUNT);

    public FundYield {
        if (fromDate == null) {
            throw new IllegalArgumentException("fromDate is required");
        }
        if (fromVcp == null) {
            throw new IllegalArgumentException("fromVcp is required");
        }
        if (toDate == null) {
            throw new IllegalArgumentException("toDate is required");
        }
        if (toVcp == null) {
            throw new IllegalArgumentException("toVcp is required");
        }
        if (fromVcp.signum() <= 0) {
            throw new IllegalArgumentException("fromVcp must be positive: " + fromVcp);
        }
        if (toVcp.signum() <= 0) {
            throw new IllegalArgumentException("toVcp must be positive: " + toVcp);
        }
        if (!toDate.isAfter(fromDate)) {
            throw new IllegalArgumentException("toDate must be after fromDate: " + fromDate + " -> " + toDate);
        }
    }

    /** Calendar days between the two quotes. */
    public int days() {
        return Math.toIntExact(ChronoUnit.DAYS.between(fromDate, toDate));
    }

    /** {@code toVcp / fromVcp − 1}. */
    public BigDecimal periodReturn() {
        return quoteRatio().subtract(BigDecimal.ONE, Money.MATH);
    }

    /** {@code (toVcp / fromVcp)^(1/n) − 1}. */
    public BigDecimal dailyRate() {
        BigDecimal exponent = BigDecimal.ONE.divide(BigDecimal.valueOf(days()), Money.MATH);
        return DecimalMath.pow(quoteRatio(), exponent, Money.MATH).subtract(BigDecimal.ONE, Money.MATH);
    }

    /** TNA-equivalent for daily compounding: {@code daily · 365}. */
    public AnnualRate tna() {
        return new AnnualRate(dailyRate().multiply(DAYS_IN_YEAR, Money.MATH));
    }

    /** {@code (toVcp / fromVcp)^(365/n) − 1}. */
    public BigDecimal tea() {
        BigDecimal exponent = DAYS_IN_YEAR.divide(BigDecimal.valueOf(days()), Money.MATH);
        return DecimalMath.pow(quoteRatio(), exponent, Money.MATH).subtract(BigDecimal.ONE, Money.MATH);
    }

    private BigDecimal quoteRatio() {
        return toVcp.divide(fromVcp, Money.MATH);
    }
}
