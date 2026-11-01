package ar.rindetuplata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PlazoFijoTest {

    private final PlazoFijo bank = PlazoFijo.of("bank-a", "Bank A", AnnualRate.ofPercent("23"));

    private static Simulation simulation(Evaluation evaluation) {
        assertThat(evaluation).isInstanceOf(Simulation.class);
        return (Simulation) evaluation;
    }

    private final PlazoFijo thirtyPercent = PlazoFijo.of("bank-c", "Bank C", AnnualRate.ofPercent("30"));

    private Simulation simulateThirtyPercent(int days) {
        return simulation(thirtyPercent.evaluate(Money.ars("1000000"), days));
    }

    @Test
    void thirtyDaysIsASingleTermOfSimpleInterest() {
        Simulation result = simulateThirtyPercent(30);

        // 1000000 * (1 + 0.30 * 30 / 365) = 1000000 * 374 / 365 = 1024657.534...
        assertThat(result.finalAmount().presented()).isEqualTo(new BigDecimal("1024657.53"));
        assertThat(result.earned().presented()).isEqualTo(new BigDecimal("24657.53"));
        assertThat(result.days()).isEqualTo(30);
    }

    @Test
    void fortyFiveDaysIsStillASingleTerm() {
        Simulation result = simulateThirtyPercent(45);

        // days < 2 * 30: one term, 1000000 * (1 + 0.30 * 45 / 365) = 1000000 * 378.5 / 365 = 1036986.301...
        assertThat(result.finalAmount().presented()).isEqualTo(new BigDecimal("1036986.30"));
    }

    @Test
    void sixtyDaysRenewsOnceAtTheEndOfTheFirstTerm() {
        Simulation result = simulateThirtyPercent(60);

        // 2 terms of 30: 1000000 * (374 / 365)^2 = 1000000 * 139876 / 133225 = 1049923.062...
        // (simple interest over 60 days would be 1049315.07)
        assertThat(result.finalAmount().presented()).isEqualTo(new BigDecimal("1049923.06"));
        assertThat(result.days()).isEqualTo(60);
    }

    @Test
    void seventyFiveDaysLetsTheLastTermAbsorbTheRemainder() {
        Simulation result = simulateThirtyPercent(75);

        // n = 75 / 30 = 2 terms: 30 and 30 + 75 % 30 = 45
        // 1000000 * (1 + 0.30 * 30 / 365) * (1 + 0.30 * 45 / 365) = 1000000 * 374 / 365 * 378.5 / 365
        //   = 1000000 * 141559 / 133225 = 1062555.826...
        assertThat(result.finalAmount().presented()).isEqualTo(new BigDecimal("1062555.83"));
    }

    @Test
    void oneHundredEightyDaysRenewsSixTimes() {
        Simulation result = simulateThirtyPercent(180);

        // 6 terms of 30: 1000000 * (374 / 365)^6 = 1157370.547...  (15.74% against 14.79% simple)
        assertThat(result.finalAmount().presented()).isEqualTo(new BigDecimal("1157370.55"));
        assertThat(result.earned().presented()).isEqualTo(new BigDecimal("157370.55"));
    }

    @Test
    void theMaximumHorizonCompoundsSixtyTerms() {
        Simulation result = simulateThirtyPercent(1825);

        // n = 1825 / 30 = 60 terms: 59 of 30 and the last 30 + 1825 % 30 = 55 (59 * 30 + 55 = 1825)
        // 1000000 * (374 / 365)^59 * (1 + 0.30 * 55 / 365) = 1000000 * (374 / 365)^59 * 381.5 / 365 = 4398931.555...
        assertThat(result.finalAmount().presented()).isEqualTo(new BigDecimal("4398931.56"));
    }

    @Test
    void renewalFollowsTheMinimumDaysOfTheDeposit() {
        PlazoFijo sixtyDay = new PlazoFijo("bank-d", "Bank D", AnnualRate.ofPercent("30"), 60, Money.ars("0"));

        // 119 days < 2 * 60: one term of simple interest: 1000000 * (1 + 0.30 * 119 / 365) = 1097808.219...
        assertThat(simulation(sixtyDay.evaluate(Money.ars("1000000"), 119)).finalAmount().presented())
                .isEqualTo(new BigDecimal("1097808.22"));
        // 120 days = 2 * 60: two terms of 60: 1000000 * (1 + 0.30 * 60 / 365)^2 = 1000000 * (383 / 365)^2 = 1101062.112...
        assertThat(simulation(sixtyDay.evaluate(Money.ars("1000000"), 120)).finalAmount().presented())
                .isEqualTo(new BigDecimal("1101062.11"));
    }

    @Test
    void sixtyDaysAtTwentyThreePercent() {
        Simulation result = simulation(bank.evaluate(Money.ars("500000"), 60));

        // 2 terms of 30 at 23%: 500000 * (1 + 0.23 * 30 / 365)^2 = 500000 * (371.9 / 365)^2 = 519082.792...
        assertThat(result.earned().presented()).isEqualTo(new BigDecimal("19082.79"));
        assertThat(result.finalAmount().presented()).isEqualTo(new BigDecimal("519082.79"));
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

    @Test
    void rejectsNonPositiveAmountAndDays() {
        assertThatThrownBy(() -> bank.evaluate(Money.ars("0"), 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be positive");
        assertThatThrownBy(() -> bank.evaluate(Money.ars("-1"), 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be positive");
        assertThatThrownBy(() -> bank.evaluate(Money.ars("1000000"), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("days must be positive");
        assertThatThrownBy(() -> bank.evaluate(Money.ars("1000000"), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("days must be positive");
    }
}
