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
}
