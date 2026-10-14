package ar.rindetuplata.domain;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Arbitrary-precision {@code ln}, {@code exp} and {@code pow} on {@link BigDecimal}, so that fractional
 * exponents (TEA conversions, REM horizon conversion) never go through {@code double}.
 */
final class DecimalMath {

    private static final int GUARD_DIGITS = 10;
    private static final BigDecimal REDUCTION_BOUND = new BigDecimal("0.1");
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private DecimalMath() {
    }

    /** {@code base^exponent} for a positive base. Integer exponents use exact repeated multiplication. */
    static BigDecimal pow(BigDecimal base, BigDecimal exponent, MathContext mc) {
        if (base.signum() <= 0) {
            throw new IllegalArgumentException("Base must be positive: " + base);
        }
        BigDecimal stripped = exponent.stripTrailingZeros();
        if (stripped.scale() <= 0 && stripped.abs().compareTo(BigDecimal.valueOf(999_999_999)) <= 0) {
            int n = stripped.intValueExact();
            MathContext work = widen(mc);
            BigDecimal power = base.pow(Math.abs(n), work);
            return (n >= 0 ? power : BigDecimal.ONE.divide(power, work)).round(mc);
        }
        MathContext work = widen(mc);
        return exp(exponent.multiply(ln(base, work), work), work).round(mc);
    }

    /** Natural logarithm of a positive number, via square-root range reduction and the atanh series. */
    static BigDecimal ln(BigDecimal x, MathContext mc) {
        if (x.signum() <= 0) {
            throw new IllegalArgumentException("ln requires a positive argument: " + x);
        }
        MathContext work = widen(mc);
        BigDecimal reduced = x;
        int halvings = 0;
        while (reduced.subtract(BigDecimal.ONE).abs().compareTo(REDUCTION_BOUND) > 0) {
            reduced = reduced.sqrt(work);
            halvings++;
        }
        BigDecimal z = reduced.subtract(BigDecimal.ONE).divide(reduced.add(BigDecimal.ONE), work);
        BigDecimal zSquared = z.multiply(z, work);
        BigDecimal epsilon = BigDecimal.ONE.movePointLeft(work.getPrecision() + 2);
        BigDecimal term = z;
        BigDecimal sum = BigDecimal.ZERO;
        for (int k = 0; term.abs().compareTo(epsilon) > 0; k++) {
            sum = sum.add(term.divide(BigDecimal.valueOf(2L * k + 1), work), work);
            term = term.multiply(zSquared, work);
        }
        BigDecimal result = sum.multiply(TWO, work);
        for (int i = 0; i < halvings; i++) {
            result = result.multiply(TWO, work);
        }
        return result.round(mc);
    }

    /** {@code e^y}, via argument halving and the Taylor series. */
    static BigDecimal exp(BigDecimal y, MathContext mc) {
        MathContext work = widen(mc);
        BigDecimal reduced = y;
        int squarings = 0;
        while (reduced.abs().compareTo(REDUCTION_BOUND) > 0) {
            reduced = reduced.divide(TWO, work);
            squarings++;
        }
        BigDecimal epsilon = BigDecimal.ONE.movePointLeft(work.getPrecision() + 2);
        BigDecimal term = BigDecimal.ONE;
        BigDecimal sum = BigDecimal.ONE;
        for (int k = 1; term.abs().compareTo(epsilon) > 0; k++) {
            term = term.multiply(reduced, work).divide(BigDecimal.valueOf(k), work);
            sum = sum.add(term, work);
        }
        for (int i = 0; i < squarings; i++) {
            sum = sum.multiply(sum, work);
        }
        return sum.round(mc);
    }

    private static MathContext widen(MathContext mc) {
        return new MathContext(mc.getPrecision() + GUARD_DIGITS, mc.getRoundingMode());
    }
}
