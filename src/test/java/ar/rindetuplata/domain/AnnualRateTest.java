package ar.rindetuplata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AnnualRateTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("1E-30");

    @Test
    void percentIsStoredAsFractionExactlyAsGiven() {
        assertThat(AnnualRate.ofPercent("20").fraction()).isEqualByComparingTo("0.20");
        assertThat(AnnualRate.ofPercent("14.13").fraction()).isEqualByComparingTo("0.1413");
        assertThat(AnnualRate.ofPercent("14.13").percent()).isEqualByComparingTo("14.13");
    }

    @Test
    void dayCountIs365() {
        assertThat(AnnualRate.DAY_COUNT).isEqualTo(365);
    }

    @Test
    void rejectsNegativeRate() {
        assertThatThrownBy(() -> AnnualRate.ofPercent("-1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dailyRateIsTnaOver365() {
        assertThat(AnnualRate.ofPercent("20").dailyRate())
                .isCloseTo(new BigDecimal("0.000547945205479452054794520547945"), within(TOLERANCE));
    }

    @Test
    void simpleInterestIsPrincipalTimesTnaTimesDaysOver365() {
        Money interest = AnnualRate.ofPercent("23").simpleInterest(Money.ars("500000"), 60);

        // 500000 * 0.23 * 60 / 365 = 18904.10958904...
        assertThat(interest.value()).isCloseTo(new BigDecimal("18904.109589041095890410958904110"), within(new BigDecimal("1E-25")));
        assertThat(interest.presented()).isEqualTo(new BigDecimal("18904.11"));
    }

    @Test
    void teaWithDailyCompounding() {
        // (1 + 0.20/365)^365 - 1
        assertThat(AnnualRate.ofPercent("20").tea(1))
                .isCloseTo(new BigDecimal("0.2213358582517385810677794695647717"), within(TOLERANCE));
    }

    @Test
    void teaWithThirtyDayCompounding() {
        // (1 + 0.23*30/365)^(365/30) - 1, fractional exponent
        assertThat(AnnualRate.ofPercent("23").tea(30))
                .isCloseTo(new BigDecimal("0.2559007481380180514784220417575735"), within(TOLERANCE));
    }

    @Test
    void teaWithAnnualCompoundingEqualsTna() {
        assertThat(AnnualRate.ofPercent("23").tea(365)).isCloseTo(new BigDecimal("0.23"), within(TOLERANCE));
    }

    @Test
    void teaRejectsNonPositivePeriod() {
        assertThatThrownBy(() -> AnnualRate.ofPercent("23").tea(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dailyCompoundingGrowth() {
        // (1 + 0.15/365)^30
        assertThat(AnnualRate.ofPercent("15").dailyCompoundingGrowth(30))
                .isCloseTo(new BigDecimal("1.012402515634879226180597130925136"), within(TOLERANCE));
    }
}
