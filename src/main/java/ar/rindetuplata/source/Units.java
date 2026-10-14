package ar.rindetuplata.source;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Unit normalization at the adapter edge. Rates become fractions ({@code 20%} is {@code 0.2}), rounded
 * {@code HALF_EVEN} to {@value #SCALE} decimals so floating-point noise in the source ({@code 7.000000000000001})
 * disappears; the domain applies its own precision afterwards.
 */
public final class Units {

    public static final int SCALE = 10;

    private Units() {
    }

    public static BigDecimal percentToFraction(BigDecimal percent) {
        return percent == null ? null : normalizeFraction(percent.movePointLeft(2));
    }

    public static BigDecimal normalizeFraction(BigDecimal fraction) {
        if (fraction == null) {
            return null;
        }
        BigDecimal rounded = fraction.setScale(SCALE, RoundingMode.HALF_EVEN).stripTrailingZeros();
        return rounded.scale() < 0 ? rounded.setScale(0) : rounded;
    }
}
