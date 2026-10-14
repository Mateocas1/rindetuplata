package ar.rindetuplata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RemuneratedAccountTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("1E-20");

    private final RemuneratedAccount naranja = RemuneratedAccount.capped(
            "naranja-x", "Naranja X", AnnualRate.ofPercent("20"), Money.ars("1000000"));

    private static Simulation simulation(Evaluation evaluation) {
        assertThat(evaluation).isInstanceOf(Simulation.class);
        return (Simulation) evaluation;
    }

    @Test
    void naranjaXExampleExactlyAtTheCapForOneDay() {
        Simulation result = simulation(naranja.evaluate(Money.ars("1000000"), 1));

        // 1000000 * 0.20 / 365 = 547.945205...
        assertThat(result.finalAmount().value())
                .isCloseTo(new BigDecimal("1000547.945205479452054794520547945"), within(TOLERANCE));
        assertThat(result.earned().presented()).isEqualTo(new BigDecimal("547.95"));
    }

    @Test
    void onePesoOverTheCapEarnsTheSameInterestOnTheCappedPartOnly() {
        Simulation atCap = simulation(naranja.evaluate(Money.ars("1000000"), 1));
        Simulation overCap = simulation(naranja.evaluate(Money.ars("1000001"), 1));

        assertThat(overCap.earned().value()).isCloseTo(atCap.earned().value(), within(TOLERANCE));
        assertThat(overCap.finalAmount().value())
                .isCloseTo(new BigDecimal("1000548.945205479452054794520547945"), within(TOLERANCE));
    }

    @Test
    void onePesoUnderTheCapEarnsOnTheWholeBalance() {
        Simulation result = simulation(naranja.evaluate(Money.ars("999999"), 1));

        assertThat(result.finalAmount().value())
                .isCloseTo(new BigDecimal("1000546.944657534246575342465753425"), within(TOLERANCE));
    }

    @Test
    void thirtyDaysAtTheCapDoesNotCompoundTheCappedPart() {
        Simulation result = simulation(naranja.evaluate(Money.ars("1000000"), 30));

        // interest capitalised above the cap earns nothing: exactly 30 * 547.9452...
        assertThat(result.finalAmount().value())
                .isCloseTo(new BigDecimal("1016438.356164383561643835616438356"), within(TOLERANCE));
        assertThat(result.finalAmount().presented()).isEqualTo(new BigDecimal("1016438.36"));
    }

    @Test
    void thirtyDaysOnePesoOverTheCap() {
        Simulation result = simulation(naranja.evaluate(Money.ars("1000001"), 30));

        assertThat(result.finalAmount().value())
                .isCloseTo(new BigDecimal("1016439.356164383561643835616438356"), within(TOLERANCE));
    }

    @Test
    void balanceBelowTheCapCompoundsDailyUntilItReachesTheCap() {
        Simulation result = simulation(naranja.evaluate(Money.ars("500000"), 30));

        assertThat(result.finalAmount().value())
                .isCloseTo(new BigDecimal("508284.8163496667993657172418852683"), within(new BigDecimal("1E-18")));
    }

    @Test
    void doubleTheCapEarnsTheSameInterestAsTheCap() {
        Simulation result = simulation(naranja.evaluate(Money.ars("2000000"), 30));

        assertThat(result.earned().presented()).isEqualTo(new BigDecimal("16438.36"));
    }

    @Test
    void excessOverTheCapEarnsTheExcessRateWhenGiven() {
        RemuneratedAccount withExcess = RemuneratedAccount.capped(
                "x", "X", AnnualRate.ofPercent("20"), Money.ars("1000000"), AnnualRate.ofPercent("10"));

        Simulation result = simulation(withExcess.evaluate(Money.ars("2000000"), 1));

        // 1000000*0.20/365 + 1000000*0.10/365 = 821.917808...
        assertThat(result.earned().value()).isCloseTo(new BigDecimal("821.917808219178082191780821918"), within(TOLERANCE));
    }

    @Test
    void orderedTiersApplyToTheBalanceBrackets() {
        RemuneratedAccount tiered = new RemuneratedAccount("t", "Tiered", List.of(
                RateTier.upTo(Money.ars("300000"), AnnualRate.ofPercent("20")),
                RateTier.upTo(Money.ars("1000000"), AnnualRate.ofPercent("10")),
                RateTier.above(AnnualRate.ofPercent("5"))), List.of());

        Simulation oneDay = simulation(tiered.evaluate(Money.ars("1500000"), 1));
        // (300000*0.20 + 700000*0.10 + 500000*0.05) / 365
        assertThat(oneDay.earned().value()).isCloseTo(new BigDecimal("424.657534246575342465753424658"), within(TOLERANCE));

        Simulation tenDays = simulation(tiered.evaluate(Money.ars("1500000"), 10));
        assertThat(tenDays.finalAmount().value())
                .isCloseTo(new BigDecimal("1504249.194050874878844050855855907"), within(new BigDecimal("1E-18")));
    }

    @Test
    void conditionalRateIsReportedAsUpToNotUsedForTheBaseResult() {
        RemuneratedAccount uala = new RemuneratedAccount("uala", "Ualá",
                List.of(RateTier.upTo(Money.ars("1000000"), AnnualRate.ofPercent("20")), RateTier.above(AnnualRate.ofPercent("0"))),
                List.of(new ConditionalRate("Spend at least $X per month", List.of(
                        RateTier.upTo(Money.ars("1000000"), AnnualRate.ofPercent("24")),
                        RateTier.above(AnnualRate.ofPercent("0"))))));

        Simulation result = simulation(uala.evaluate(Money.ars("1000000"), 30));

        assertThat(result.finalAmount().presented()).isEqualTo(new BigDecimal("1016438.36"));
        assertThat(result.conditional()).isPresent();
        ConditionalOutcome upTo = result.conditional().orElseThrow();
        assertThat(upTo.condition()).isEqualTo("Spend at least $X per month");
        assertThat(upTo.finalAmount().presented()).isEqualTo(new BigDecimal("1019726.03"));
    }

    @Test
    void bestConditionalOutcomeIsReported() {
        List<RateTier> low = List.of(RateTier.upTo(Money.ars("1000000"), AnnualRate.ofPercent("22")),
                RateTier.above(AnnualRate.ofPercent("0")));
        List<RateTier> high = List.of(RateTier.upTo(Money.ars("1000000"), AnnualRate.ofPercent("24")),
                RateTier.above(AnnualRate.ofPercent("0")));
        RemuneratedAccount account = new RemuneratedAccount("a", "A", List.of(
                RateTier.upTo(Money.ars("1000000"), AnnualRate.ofPercent("20")), RateTier.above(AnnualRate.ofPercent("0"))),
                List.of(new ConditionalRate("low", low), new ConditionalRate("high", high)));

        assertThat(simulation(account.evaluate(Money.ars("1000000"), 30)).conditional().orElseThrow().condition())
                .isEqualTo("high");
    }

    @Test
    void rejectsTiersWithUnorderedCeilings() {
        assertThatThrownBy(() -> new RemuneratedAccount("t", "T", List.of(
                RateTier.upTo(Money.ars("1000000"), AnnualRate.ofPercent("20")),
                RateTier.upTo(Money.ars("500000"), AnnualRate.ofPercent("10"))), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnUnboundedTierThatIsNotLast() {
        assertThatThrownBy(() -> new RemuneratedAccount("t", "T", List.of(
                RateTier.above(AnnualRate.ofPercent("20")),
                RateTier.upTo(Money.ars("500000"), AnnualRate.ofPercent("10"))), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyTiers() {
        assertThatThrownBy(() -> new RemuneratedAccount("t", "T", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
