package ar.rindetuplata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.math.MathContext;
import org.junit.jupiter.api.Test;

class DecimalMathTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("1E-30");

    @Test
    void integerExponentIsExactPower() {
        BigDecimal result = DecimalMath.pow(new BigDecimal("1.5"), new BigDecimal("3"), MathContext.DECIMAL128);

        assertThat(result).isEqualByComparingTo("3.375");
    }

    @Test
    void halfExponentIsSquareRoot() {
        BigDecimal result = DecimalMath.pow(new BigDecimal("2"), new BigDecimal("0.5"), MathContext.DECIMAL128);

        assertThat(result).isCloseTo(new BigDecimal("1.414213562373095048801688724209698"), within(TOLERANCE));
    }

    @Test
    void fractionalExponentOfOnePointFive() {
        BigDecimal result = DecimalMath.pow(new BigDecimal("1.5"), new BigDecimal("0.5"), MathContext.DECIMAL128);

        assertThat(result).isCloseTo(new BigDecimal("1.224744871391589049098642037352946"), within(TOLERANCE));
    }

    @Test
    void naturalLogarithmOfTwo() {
        BigDecimal result = DecimalMath.ln(new BigDecimal("2"), MathContext.DECIMAL128);

        assertThat(result).isCloseTo(new BigDecimal("0.6931471805599453094172321214581766"), within(TOLERANCE));
    }

    @Test
    void exponentialOfOne() {
        BigDecimal result = DecimalMath.exp(BigDecimal.ONE, MathContext.DECIMAL128);

        assertThat(result).isCloseTo(new BigDecimal("2.718281828459045235360287471352662"), within(TOLERANCE));
    }

    @Test
    void powerOfOneIsOne() {
        BigDecimal result = DecimalMath.pow(BigDecimal.ONE, new BigDecimal("12.1666"), MathContext.DECIMAL128);

        assertThat(result).isEqualByComparingTo("1");
    }
}
