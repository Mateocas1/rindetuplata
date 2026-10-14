package ar.rindetuplata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyMarketFundTest {

    private final MoneyMarketFund fund = new MoneyMarketFund("fund", "Fund", AnnualRate.ofPercent("15"));

    @Test
    void compoundsDailyOverTheHorizon() {
        Simulation result = (Simulation) fund.evaluate(Money.ars("1000000"), 30);

        // 1000000 * (1 + 0.15/365)^30
        assertThat(result.finalAmount().value())
                .isCloseTo(new BigDecimal("1012402.515634879226180597130925136"), within(new BigDecimal("1E-20")));
        assertThat(result.finalAmount().presented()).isEqualTo(new BigDecimal("1012402.52"));
    }

    @Test
    void oneDayIsSimpleDailyInterest() {
        Simulation result = (Simulation) fund.evaluate(Money.ars("1000000"), 1);

        assertThat(result.finalAmount().presented()).isEqualTo(new BigDecimal("1000410.96"));
    }

    @Test
    void hasNoMinimumTerm() {
        assertThat(fund.evaluate(Money.ars("1"), 1)).isInstanceOf(Simulation.class);
    }

    @Test
    void noCapSoRateAppliesToAnyAmount() {
        Simulation result = (Simulation) fund.evaluate(Money.ars("2000000"), 30);

        assertThat(result.earned().presented()).isEqualTo(new BigDecimal("24805.03"));
    }
}
