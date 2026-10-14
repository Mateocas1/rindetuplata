package ar.rindetuplata.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class UnitsTest {

    @Test
    void convertsPercentToFraction() {
        assertThat(Units.percentToFraction(new BigDecimal("21.1"))).isEqualByComparingTo("0.211");
        assertThat(Units.percentToFraction(new BigDecimal("0.10"))).isEqualByComparingTo("0.001");
    }

    @Test
    void removesFloatingPointNoiseFromSourceNumbers() {
        assertThat(Units.percentToFraction(new BigDecimal("7.000000000000001"))).isEqualByComparingTo("0.07");
    }

    @Test
    void resultHasNoNegativeScale() {
        assertThat(Units.percentToFraction(new BigDecimal("100000")).scale()).isNotNegative();
    }

    @Test
    void keepsNullAsNull() {
        assertThat(Units.percentToFraction(null)).isNull();
    }

    @Test
    void roundsFractionsHalfEvenToTenDecimals() {
        assertThat(Units.normalizeFraction(new BigDecimal("0.12345678905"))).isEqualByComparingTo("0.1234567890");
        assertThat(Units.normalizeFraction(new BigDecimal("0.1925"))).isEqualByComparingTo("0.1925");
    }

    @Test
    void keepsNegativeValuesForTheValidationLayer() {
        assertThat(Units.percentToFraction(new BigDecimal("-1"))).isEqualByComparingTo("-0.01");
    }
}
