package ar.rindetuplata.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * The dollar MEP break-even of an instrument (S5, L24 b). Buying dollars costs the MEP {@code sell} today and
 * leaving them costs the MEP {@code buy} at the end, so an instrument with nominal return {@code r} beats the MEP
 * when the MEP buy price rises less than {@code devaluation = (1 + r) * sell / buy - 1}; {@code price} is the
 * break-even MEP buy price, {@code (1 + r) * sell}. The spread is part of the answer, not ignored: with the usual
 * {@code sell > buy} the devaluation is above {@code r}.
 *
 * <p>Worked example: {@code r = 0.025}, buy 1534.20, sell 1539.40 gives price 1577.885 (1577.88) and devaluation
 * 1577.885 / 1534.20 - 1 = 0.028474.
 */
public record MepBreakEven(BigDecimal devaluation, BigDecimal price) {

    public static final int DEVALUATION_SCALE = 6;
    public static final int PRICE_SCALE = Money.PRESENTED_SCALE;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    public MepBreakEven {
        Objects.requireNonNull(devaluation, "devaluation");
        Objects.requireNonNull(price, "price");
    }

    /** Both quote prices must be positive; the devaluation is a fraction (2% is {@code 0.02}). */
    public static MepBreakEven of(BigDecimal nominalReturn, BigDecimal buy, BigDecimal sell) {
        if (buy.signum() <= 0 || sell.signum() <= 0) {
            throw new IllegalArgumentException("MEP buy and sell must be positive: buy=" + buy + ", sell=" + sell);
        }
        BigDecimal growth = BigDecimal.ONE.add(nominalReturn);
        BigDecimal price = growth.multiply(sell, Money.MATH);
        BigDecimal devaluation = price.divide(buy, Money.MATH).subtract(BigDecimal.ONE);
        return new MepBreakEven(devaluation.setScale(DEVALUATION_SCALE, ROUNDING),
                price.setScale(PRICE_SCALE, ROUNDING));
    }
}
