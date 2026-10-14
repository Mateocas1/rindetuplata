package ar.rindetuplata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void presentedRoundsHalfEvenToTwoDecimals() {
        assertThat(Money.ars("0.125").presented()).isEqualTo(new BigDecimal("0.12"));
        assertThat(Money.ars("0.135").presented()).isEqualTo(new BigDecimal("0.14"));
        assertThat(Money.ars("2.675").presented()).isEqualTo(new BigDecimal("2.68"));
        assertThat(Money.ars("18904.10958904").presented()).isEqualTo(new BigDecimal("18904.11"));
    }

    @Test
    void presentedAlwaysHasScaleTwo() {
        assertThat(Money.ars("5").presented().scale()).isEqualTo(2);
    }

    @Test
    void internalMathKeepsFullPrecision() {
        Money third = Money.ars("1").dividedBy(new BigDecimal("3"));

        assertThat(third.value().scale()).isGreaterThan(20);
        assertThat(third.times(new BigDecimal("3")).presented()).isEqualTo(new BigDecimal("1.00"));
    }

    @Test
    void plusMinusTimesAndComparison() {
        Money a = Money.ars("100.50");
        Money b = Money.ars("0.25");

        assertThat(a.plus(b).value()).isEqualByComparingTo("100.75");
        assertThat(a.minus(b).value()).isEqualByComparingTo("100.25");
        assertThat(a.times(new BigDecimal("0.1")).value()).isEqualByComparingTo("10.05");
        assertThat(a.compareTo(b)).isPositive();
        assertThat(b.isLessThan(a)).isTrue();
        assertThat(a.isLessThan(a)).isFalse();
    }

    @Test
    void equalityIgnoresScale() {
        assertThat(Money.ars("1000000")).isEqualTo(Money.ars("1000000.00"));
        assertThat(Money.ars("1000000").hashCode()).isEqualTo(Money.ars("1000000.00").hashCode());
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> Money.ars((BigDecimal) null)).isInstanceOf(NullPointerException.class);
    }
}
