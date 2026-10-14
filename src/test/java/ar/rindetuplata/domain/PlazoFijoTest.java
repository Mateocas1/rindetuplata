package ar.rindetuplata.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PlazoFijoTest {

    private final PlazoFijo bank = PlazoFijo.of("bank-a", "Bank A", AnnualRate.ofPercent("23"));

    private static Simulation simulation(Evaluation evaluation) {
        assertThat(evaluation).isInstanceOf(Simulation.class);
        return (Simulation) evaluation;
    }

    @Test
    void simpleInterestForTheTerm() {
        Simulation result = simulation(bank.evaluate(Money.ars("500000"), 60));

        // 500000 * 0.23 * 60 / 365 = 18904.11
        assertThat(result.earned().presented()).isEqualTo(new BigDecimal("18904.11"));
        assertThat(result.finalAmount().presented()).isEqualTo(new BigDecimal("518904.11"));
        assertThat(result.days()).isEqualTo(60);
    }

    @Test
    void nominalReturnIsEarnedOverInitial() {
        Simulation result = simulation(bank.evaluate(Money.ars("1000000"), 30));

        // 0.23 * 30 / 365
        assertThat(result.nominalReturn())
                .isCloseTo(new BigDecimal("0.01890410958904109589041095890411"), org.assertj.core.api.Assertions.within(new BigDecimal("1E-30")));
    }

    @Test
    void thirtyDaysIsEligible() {
        assertThat(bank.evaluate(Money.ars("1000000"), 30)).isInstanceOf(Simulation.class);
    }

    @Test
    void twentyNineDaysIsBelowMinimumTerm() {
        Evaluation evaluation = bank.evaluate(Money.ars("1000000"), 29);

        assertThat(evaluation).isInstanceOf(Ineligible.class);
        Ineligible ineligible = (Ineligible) evaluation;
        assertThat(ineligible.reason()).isEqualTo(Ineligible.Reason.MINIMUM_TERM);
        assertThat(ineligible.detail()).contains("30").contains("29");
    }

    @Test
    void minimumAmountBoundary() {
        PlazoFijo withMinimum = new PlazoFijo("bank-b", "Bank B", AnnualRate.ofPercent("23"), 30, Money.ars("10000"));

        assertThat(withMinimum.evaluate(Money.ars("10000"), 30)).isInstanceOf(Simulation.class);

        Evaluation below = withMinimum.evaluate(Money.ars("9999.99"), 30);
        assertThat(below).isInstanceOf(Ineligible.class);
        assertThat(((Ineligible) below).reason()).isEqualTo(Ineligible.Reason.MINIMUM_AMOUNT);
        assertThat(((Ineligible) below).detail()).contains("10000");
    }

    @Test
    void termIsCheckedBeforeAmount() {
        PlazoFijo both = new PlazoFijo("bank-b", "Bank B", AnnualRate.ofPercent("23"), 30, Money.ars("10000"));

        Ineligible ineligible = (Ineligible) both.evaluate(Money.ars("1"), 1);

        assertThat(ineligible.reason()).isEqualTo(Ineligible.Reason.MINIMUM_TERM);
    }

    @Test
    void hasNoConditionalOutcome() {
        assertThat(simulation(bank.evaluate(Money.ars("1000000"), 30)).conditional()).isEmpty();
    }
}
