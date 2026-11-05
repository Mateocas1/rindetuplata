package ar.rindetuplata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InflationTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("1E-30");

    @Test
    void remTwelveMonthsConvertedToThirtyDaysByCompounding() {
        // (1.211)^(30/365) - 1, not 0.211 * 30/365 = 0.01734
        BigDecimal horizon = Inflation.overHorizon(new BigDecimal("0.211"), 30);

        assertThat(horizon).isCloseTo(new BigDecimal("0.01585977800321261535230024978641785"), within(TOLERANCE));
        assertThat(horizon).isNotEqualByComparingTo(new BigDecimal("0.211").multiply(new BigDecimal("30")).divide(new BigDecimal("365"), 10, java.math.RoundingMode.HALF_EVEN));
    }

    @Test
    void remOverNinetyDays() {
        assertThat(Inflation.overHorizon(new BigDecimal("0.21"), 90))
                .isCloseTo(new BigDecimal("0.04812439927373415452016752887195150"), within(TOLERANCE));
    }

    @Test
    void remOverAYearIsTheRemItself() {
        assertThat(Inflation.overHorizon(new BigDecimal("0.211"), 365)).isCloseTo(new BigDecimal("0.211"), within(TOLERANCE));
    }

    @Test
    void zeroDaysIsZeroInflation() {
        assertThat(Inflation.overHorizon(new BigDecimal("0.211"), 0)).isEqualByComparingTo("0");
    }

    @Test
    void monthlyIndecRatesCompoundForHistory() {
        // 1.019 * 1.023 * 1.015 - 1
        BigDecimal cumulative = Inflation.cumulative(
                List.of(new BigDecimal("0.019"), new BigDecimal("0.023"), new BigDecimal("0.015")));

        assertThat(cumulative).isEqualByComparingTo("0.058073555");
    }

    @Test
    void emptyHistoryIsZero() {
        assertThat(Inflation.cumulative(List.of())).isEqualByComparingTo("0");
    }

    @Test
    void realReturnIsQuotientNotDifference() {
        // 1.01 / 1.02 - 1 = -0.0098039..., not 0.01 - 0.02
        BigDecimal real = Inflation.realReturn(new BigDecimal("0.01"), new BigDecimal("0.02"));

        assertThat(real).isCloseTo(new BigDecimal("-0.009803921568627450980392156862745"), within(TOLERANCE));
    }

    @Test
    void realReturnIsZeroWhenNominalEqualsInflation() {
        assertThat(Inflation.realReturn(new BigDecimal("0.02"), new BigDecimal("0.02"))).isEqualByComparingTo("0");
    }

    @Test
    void realEarnedIsThePesosAboveExpectedInflationInTodaysPesos() {
        // 1,019,726.03 / 1.01586 - 1,000,000 = 3,805.67
        assertThat(Inflation.realEarned(Money.ars("1000000"), Money.ars("1019726.03"), new BigDecimal("0.01586")))
                .isEqualTo(new BigDecimal("3805.67"));
    }

    @Test
    void realEarnedIsNegativeWhenInflationBeatsTheInvestment() {
        // 1,010,000 / 1.02 - 1,000,000 = -9,803.9215...
        assertThat(Inflation.realEarned(Money.ars("1000000"), Money.ars("1010000"), new BigDecimal("0.02")))
                .isEqualTo(new BigDecimal("-9803.92"));
    }

    @Test
    void realEarnedWithZeroInflationIsTheNominalEarnings() {
        assertThat(Inflation.realEarned(Money.ars("500000"), Money.ars("512345.67"), BigDecimal.ZERO))
                .isEqualTo(new BigDecimal("12345.67"));
    }

    @Test
    void realEarnedIsRoundedToCentavosHalfEvenAtScaleTwo() {
        // With 100% inflation the factor is exactly 2, so each case lands on an exact half centavo.
        assertThat(Inflation.realEarned(Money.ars("50"), Money.ars("100.01"), BigDecimal.ONE))
                .isEqualTo(new BigDecimal("0.00"));
        assertThat(Inflation.realEarned(Money.ars("50"), Money.ars("100.03"), BigDecimal.ONE))
                .isEqualTo(new BigDecimal("0.02"));
        assertThat(Inflation.realEarned(Money.ars("50"), Money.ars("100.05"), BigDecimal.ONE))
                .isEqualTo(new BigDecimal("0.02"));
        assertThat(Inflation.realEarned(Money.ars("50"), Money.ars("99.97"), BigDecimal.ONE))
                .isEqualTo(new BigDecimal("-0.02"));
        assertThat(Inflation.realEarned(Money.ars("50"), Money.ars("100"), BigDecimal.ONE).scale()).isEqualTo(2);
    }

    @Test
    void realEarnedRejectsInflationOfMinusOneHundredPercentOrLess() {
        assertThatThrownBy(() -> Inflation.realEarned(Money.ars("100"), Money.ars("101"), new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInflationOfMinusOneHundredPercentOrLess() {
        assertThatThrownBy(() -> Inflation.realReturn(new BigDecimal("0.01"), new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeDays() {
        assertThatThrownBy(() -> Inflation.overHorizon(new BigDecimal("0.211"), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("days must not be negative");
    }

    @Test
    void monthlyInflationIsAnnualizedByCompoundingTwelveMonths() {
        // 1.02^12 - 1, not 0.02 * 12 = 0.24
        assertThat(Inflation.annualized(new BigDecimal("0.02")))
                .isCloseTo(new BigDecimal("0.268241794562545318301696"), within(TOLERANCE));
        assertThat(Inflation.annualized(new BigDecimal("0.019")))
                .isCloseTo(new BigDecimal("0.253401494152226722337275816563694161"), within(TOLERANCE));
    }

    @Test
    void annualizingZeroMonthlyInflationIsZeroAndDeflationStaysNegative() {
        assertThat(Inflation.annualized(BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThat(Inflation.annualized(new BigDecimal("-0.005")))
                .isCloseTo(new BigDecimal("-0.058377193085624182069668554443359375"), within(TOLERANCE));
    }

    @Test
    void annualizingRejectsMonthlyInflationOfMinusOneHundredPercentOrLess() {
        assertThatThrownBy(() -> Inflation.annualized(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
