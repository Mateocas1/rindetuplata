package ar.rindetuplata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MepBreakEvenTest {

    @Test
    void workedExample_2_5PercentOverTheRecordedQuote_1534_20Buy_1539_40Sell() {
        // price = 1.025 * 1539.40 = 1577.885 -> 1577.88 (HALF_EVEN keeps the even 8, HALF_UP would say 1577.89)
        // X = 1577.885 / 1534.20 - 1 = 0.028474123... -> 0.028474
        MepBreakEven breakEven = MepBreakEven.of(new BigDecimal("0.025"), new BigDecimal("1534.20"),
                new BigDecimal("1539.40"));

        assertThat(breakEven.devaluation()).isEqualTo(new BigDecimal("0.028474"));
        assertThat(breakEven.price()).isEqualTo(new BigDecimal("1577.88"));
    }

    @Test
    void theSpreadRaisesTheDevaluationTheInstrumentToleratesAboveItsOwnReturn() {
        // You buy at the sell price and leave at the buy price, so the dollar starts behind by sell/buy.
        BigDecimal r = new BigDecimal("0.025");

        MepBreakEven withSpread = MepBreakEven.of(r, new BigDecimal("1534.20"), new BigDecimal("1539.40"));
        MepBreakEven withoutSpread = MepBreakEven.of(r, new BigDecimal("1539.40"), new BigDecimal("1539.40"));

        assertThat(withoutSpread.devaluation()).isEqualTo(new BigDecimal("0.025000"));
        assertThat(withSpread.devaluation()).isGreaterThan(withoutSpread.devaluation());
    }

    @Test
    void anInvertedQuoteWithSellBelowBuyLowersTheDevaluationUnderTheReturn() {
        // 1.025 * 1500 / 1540 - 1 = -0.001623...: X < r when the source reports sell under buy.
        MepBreakEven breakEven = MepBreakEven.of(new BigDecimal("0.025"), new BigDecimal("1540"),
                new BigDecimal("1500"));

        assertThat(breakEven.devaluation()).isEqualTo(new BigDecimal("-0.001623"));
        assertThat(breakEven.devaluation()).isLessThan(new BigDecimal("0.025"));
        assertThat(breakEven.price()).isEqualTo(new BigDecimal("1537.50"));
    }

    @Test
    void theDevaluationRoundsHalfEvenAtSixDecimals() {
        // sell / buy - 1 = 0.0000015 exactly: HALF_EVEN gives 0.000002 (HALF_DOWN would give 0.000001).
        MepBreakEven breakEven = MepBreakEven.of(BigDecimal.ZERO, new BigDecimal("1000000"),
                new BigDecimal("1000001.5"));

        assertThat(breakEven.devaluation()).isEqualTo(new BigDecimal("0.000002"));
    }

    @Test
    void aZeroReturnBreaksEvenAtTheSellPrice() {
        MepBreakEven breakEven = MepBreakEven.of(BigDecimal.ZERO, new BigDecimal("1534.20"),
                new BigDecimal("1539.40"));

        assertThat(breakEven.price()).isEqualTo(new BigDecimal("1539.40"));
    }

    @Test
    void aNonPositiveQuoteHasNoBreakEven() {
        assertThatThrownBy(() -> MepBreakEven.of(new BigDecimal("0.02"), BigDecimal.ZERO, new BigDecimal("1539.40")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MepBreakEven.of(new BigDecimal("0.02"), new BigDecimal("1534.20"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
