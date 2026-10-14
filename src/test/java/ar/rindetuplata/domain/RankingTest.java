package ar.rindetuplata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RankingTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("1E-20");
    private static final BigDecimal REM = new BigDecimal("0.211");

    private final PlazoFijo plazoFijo = PlazoFijo.of("pf", "Plazo fijo 23%", AnnualRate.ofPercent("23"));
    private final RemuneratedAccount naranja = RemuneratedAccount.capped(
            "naranja-x", "Naranja X", AnnualRate.ofPercent("20"), Money.ars("1000000"));
    private final MoneyMarketFund fund = new MoneyMarketFund("fund", "Fund 15%", AnnualRate.ofPercent("15"));
    private final PlazoFijo bigTicket = new PlazoFijo(
            "big", "Big ticket", AnnualRate.ofPercent("30"), 30, Money.ars("5000000"));

    private List<Instrument> all() {
        return List.of(fund, naranja, bigTicket, plazoFijo);
    }

    @Test
    void defaultsAreOneMillionPesosForThirtyDays() {
        assertThat(Ranking.DEFAULT_AMOUNT).isEqualTo(Money.ars("1000000"));
        assertThat(Ranking.DEFAULT_DAYS).isEqualTo(30);

        Ranking ranking = Ranking.withDefaults(all(), REM);

        assertThat(ranking.amount()).isEqualTo(Money.ars("1000000"));
        assertThat(ranking.days()).isEqualTo(30);
    }

    @Test
    void sortsByFinalAmountDescendingAtTheDefaults() {
        Ranking ranking = Ranking.withDefaults(all(), REM);

        assertThat(ranking.ranked()).extracting(e -> e.instrument().id())
                .containsExactly("pf", "naranja-x", "fund");
        assertThat(ranking.ranked().get(0).simulation().finalAmount().presented())
                .isEqualTo(new BigDecimal("1018904.11"));
        assertThat(ranking.ranked().get(1).simulation().finalAmount().presented())
                .isEqualTo(new BigDecimal("1016438.36"));
        assertThat(ranking.ranked().get(2).simulation().finalAmount().presented())
                .isEqualTo(new BigDecimal("1012402.52"));
    }

    @Test
    void ineligibleInstrumentsAreReturnedSeparatelyWithTheirReason() {
        Ranking ranking = Ranking.withDefaults(all(), REM);

        assertThat(ranking.ineligible()).hasSize(1);
        assertThat(ranking.ineligible().get(0).instrument().id()).isEqualTo("big");
        assertThat(ranking.ineligible().get(0).ineligible().reason()).isEqualTo(Ineligible.Reason.MINIMUM_AMOUNT);
    }

    @Test
    void personalizedAmountChangesTheOrderBecauseOfTheCap() {
        Ranking ranking = Ranking.of(all(), Money.ars("2000000"), 30, REM);

        // plazo fijo 37808.22 > fund 24805.03 > Naranja X capped 16438.36
        assertThat(ranking.ranked()).extracting(e -> e.instrument().id())
                .containsExactly("pf", "fund", "naranja-x");
    }

    @Test
    void bigTicketBecomesEligibleAtItsMinimumAmount() {
        Ranking ranking = Ranking.of(all(), Money.ars("5000000"), 30, REM);

        assertThat(ranking.ranked().get(0).instrument().id()).isEqualTo("big");
        assertThat(ranking.ineligible()).isEmpty();
    }

    @Test
    void twentyNineDaysExcludesPlazoFijoForMinimumTermButKeepsTheOthers() {
        Ranking ranking = Ranking.of(all(), Money.ars("1000000"), 29, REM);

        assertThat(ranking.ranked()).extracting(e -> e.instrument().id()).containsExactly("naranja-x", "fund");
        assertThat(ranking.ineligible()).extracting(r -> r.instrument().id()).containsExactlyInAnyOrder("pf", "big");
        assertThat(ranking.ineligible()).allSatisfy(r ->
                assertThat(r.ineligible().reason()).isEqualTo(Ineligible.Reason.MINIMUM_TERM));
    }

    @Test
    void realReturnUsesExpectedInflationOverTheSameHorizon() {
        Ranking ranking = Ranking.withDefaults(all(), REM);

        assertThat(ranking.inflationOverHorizon())
                .isCloseTo(new BigDecimal("0.01585977800321261535230024978641785"), within(TOLERANCE));
        // (1 + 0.23*30/365) / (1.211^(30/365)) - 1
        assertThat(ranking.ranked().get(0).realReturn())
                .isCloseTo(new BigDecimal("0.002996802956223406024965905314253575"), within(new BigDecimal("1E-18")));
        // fund loses against inflation
        assertThat(ranking.ranked().get(2).realReturn().signum()).isNegative();
        assertThat(ranking.ranked().get(2).realReturn())
                .isCloseTo(new BigDecimal("-0.003403286992156564859745665549161200"), within(new BigDecimal("1E-18")));
    }

    @Test
    void equalOutcomesAreOrderedByName() {
        MoneyMarketFund b = new MoneyMarketFund("b", "B", AnnualRate.ofPercent("15"));
        MoneyMarketFund a = new MoneyMarketFund("a", "A", AnnualRate.ofPercent("15"));

        Ranking ranking = Ranking.withDefaults(List.of(b, a), REM);

        assertThat(ranking.ranked()).extracting(e -> e.instrument().id()).containsExactly("a", "b");
    }

    @Test
    void conditionalOutcomeTravelsWithTheRankedEntry() {
        RemuneratedAccount uala = new RemuneratedAccount("uala", "Ualá",
                List.of(RateTier.upTo(Money.ars("1000000"), AnnualRate.ofPercent("20")), RateTier.above(AnnualRate.ofPercent("0"))),
                List.of(new ConditionalRate("Spend $X", List.of(
                        RateTier.upTo(Money.ars("1000000"), AnnualRate.ofPercent("24")),
                        RateTier.above(AnnualRate.ofPercent("0"))))));

        Ranking ranking = Ranking.withDefaults(List.of(uala), REM);

        assertThat(ranking.ranked().get(0).simulation().conditional()).isPresent();
    }

    @Test
    void rejectsNonPositiveAmountAndDays() {
        assertThatThrownBy(() -> Ranking.of(all(), Money.ars("0"), 30, REM)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ranking.of(all(), Money.ars("1000"), 0, REM)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyInstrumentListGivesEmptyRanking() {
        Ranking ranking = Ranking.withDefaults(List.of(), REM);

        assertThat(ranking.ranked()).isEmpty();
        assertThat(ranking.ineligible()).isEmpty();
    }
}
