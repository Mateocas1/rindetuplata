package ar.rindetuplata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FundYieldTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("1E-30");

    /** Friday to Monday: the fund accrued over the whole weekend. */
    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 9);
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);

    private static FundYield oneDay() {
        return new FundYield(FRIDAY, new BigDecimal("1000.123456"), LocalDate.of(2026, 10, 10), new BigDecimal("1000.987654"));
    }

    private static FundYield weekend() {
        return new FundYield(FRIDAY, new BigDecimal("25000.000000"), MONDAY, new BigDecimal("25021.456789"));
    }

    private static FundYield negative() {
        return new FundYield(FRIDAY, new BigDecimal("500.123456"), LocalDate.of(2026, 10, 11), new BigDecimal("499.001234"));
    }

    @Test
    void periodReturnIsTheVcpRatioMinusOne() {
        // 1000.987654 / 1000.123456 - 1
        assertThat(oneDay().periodReturn())
                .isCloseTo(
                        new BigDecimal("0.00086409132274165960567172219186508110454715702618217565332"),
                        within(TOLERANCE));
    }

    @Test
    void overOneDayTheDailyRateEqualsThePeriodReturn() {
        assertThat(oneDay().dailyRate())
                .isCloseTo(
                        new BigDecimal("0.00086409132274165960567172219186508110454715702618217565332"),
                        within(TOLERANCE));
    }

    @Test
    void teaOverOneDayIsTheAnnualisedPeriodReturn() {
        // (1000.987654 / 1000.123456)^365 - 1
        assertThat(oneDay().tea())
                .isCloseTo(
                        new BigDecimal("0.37061171423351300401072525302308062777037111072707682167921"),
                        within(TOLERANCE));
    }

    @Test
    void tnaEquivalentIsTheDailyRateTimes365() {
        // ((1000.987654 / 1000.123456)^(1/1) - 1) * 365
        assertThat(oneDay().tna().fraction())
                .isCloseTo(
                        new BigDecimal("0.3153933328007057560701786000307546031597123145564941134618"),
                        within(TOLERANCE));
    }

    @Test
    void weekendGapCountsCalendarDays() {
        assertThat(weekend().days()).isEqualTo(3);
    }

    @Test
    void weekendGapSpreadsTheReturnOverThreeCalendarDays() {
        // 25021.456789 / 25000.000000 - 1
        assertThat(weekend().periodReturn()).isCloseTo(new BigDecimal("0.00085827156"), within(TOLERANCE));

        // (25021.456789 / 25000.000000)^(1/3) - 1
        assertThat(weekend().dailyRate())
                .isCloseTo(
                        new BigDecimal("0.00028600871121850922952685668809232359913699768511809717546"),
                        within(TOLERANCE));

        // ((25021.456789 / 25000.000000)^(1/3) - 1) * 365
        assertThat(weekend().tna().fraction())
                .isCloseTo(
                        new BigDecimal("0.1043931795947558687773026911536981136850041550681054690429"),
                        within(TOLERANCE));

        // (25021.456789 / 25000.000000)^(365/3) - 1
        assertThat(weekend().tea())
                .isCloseTo(
                        new BigDecimal("0.11002024484970100346264924307587766798708432062413538488807"),
                        within(TOLERANCE));
    }

    @Test
    void tnaEquivalentFeedsMoneyMarketFundDailyCompounding() {
        FundYield yield = weekend();

        Money finalAmount = ((Simulation) new MoneyMarketFund("mercado-fondo", "Mercado Fondo", yield.tna())
                        .evaluate(Money.ars("25000"), yield.days()))
                .finalAmount();

        // daily compounding at the TNA-equivalent reproduces the observed quote: 25021.456789
        assertThat(finalAmount.value()).isCloseTo(new BigDecimal("25021.456789"), within(new BigDecimal("1E-20")));
    }

    @Test
    void negativeReturnStaysNegative() {
        // 499.001234 / 500.123456 - 1
        assertThat(negative().periodReturn())
                .isCloseTo(
                        new BigDecimal("-0.002243889956643025357322972670172062475709997493098983943676"),
                        within(TOLERANCE));

        // (499.001234 / 500.123456)^(1/2) - 1
        assertThat(negative().dailyRate())
                .isCloseTo(
                        new BigDecimal("-0.001122575065710590246666578905183173702637578102253907795739"),
                        within(TOLERANCE));

        // (499.001234 / 500.123456)^(365/2) - 1
        assertThat(negative().tea())
                .isCloseTo(
                        new BigDecimal("-0.336329875325656819936991076965157537278830361797722142882065"),
                        within(TOLERANCE));
    }

    @Test
    void negativeYieldHasNoTnaEquivalentBecauseAnnualRateRejectsNegativeRates() {
        assertThatThrownBy(() -> negative().tna()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalQuotesYieldZero() {
        FundYield flat = new FundYield(FRIDAY, new BigDecimal("1234.5"), MONDAY, new BigDecimal("1234.5"));

        assertThat(flat.periodReturn()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(flat.dailyRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(flat.tea()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(flat.tna().fraction()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsToDateNotAfterFromDate() {
        assertThatThrownBy(() -> new FundYield(FRIDAY, new BigDecimal("100"), FRIDAY, new BigDecimal("101")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toDate must be after fromDate: 2026-10-09 -> 2026-10-09");
        assertThatThrownBy(() -> new FundYield(MONDAY, new BigDecimal("100"), FRIDAY, new BigDecimal("101")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toDate must be after fromDate: 2026-10-12 -> 2026-10-09");
    }

    @Test
    void rejectsNulls() {
        assertThatThrownBy(() -> new FundYield(null, new BigDecimal("100"), MONDAY, new BigDecimal("101")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fromDate is required");
        assertThatThrownBy(() -> new FundYield(FRIDAY, null, MONDAY, new BigDecimal("101")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fromVcp is required");
        assertThatThrownBy(() -> new FundYield(FRIDAY, new BigDecimal("100"), null, new BigDecimal("101")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toDate is required");
        assertThatThrownBy(() -> new FundYield(FRIDAY, new BigDecimal("100"), MONDAY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toVcp is required");
    }

    @Test
    void rejectsNonPositiveVcp() {
        assertThatThrownBy(() -> new FundYield(FRIDAY, BigDecimal.ZERO, MONDAY, new BigDecimal("101")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fromVcp must be positive: 0");
        assertThatThrownBy(() -> new FundYield(FRIDAY, new BigDecimal("-0.01"), MONDAY, new BigDecimal("101")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fromVcp must be positive: -0.01");
        assertThatThrownBy(() -> new FundYield(FRIDAY, new BigDecimal("100"), MONDAY, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toVcp must be positive: 0");
        assertThatThrownBy(() -> new FundYield(FRIDAY, new BigDecimal("100"), MONDAY, new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toVcp must be positive: -0.01");
    }
}
