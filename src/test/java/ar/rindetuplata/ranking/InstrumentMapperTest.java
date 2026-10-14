package ar.rindetuplata.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.domain.AnnualRate;
import ar.rindetuplata.domain.ConditionalRate;
import ar.rindetuplata.domain.Money;
import ar.rindetuplata.domain.RateTier;
import ar.rindetuplata.source.plazofijo.TermRate;
import ar.rindetuplata.source.wallet.WalletCondition;
import ar.rindetuplata.source.wallet.WalletTier;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * T5a pure mapping rules: stored wallet tiers and conditions become domain brackets that cover the whole balance
 * (a floor or a gap earns nothing, a bounded top earns nothing above it), a crypto APY becomes its TNA-equivalent
 * for daily compounding, and a bank term band is chosen by days and amount windows. Hand-computed values.
 */
class InstrumentMapperTest {

	@Test
	void walletTierWithFloorGetsZeroBelowAndZeroAbove() {
		List<RateTier> tiers = InstrumentMapper.rateTiers(
				List.of(new WalletTier(new BigDecimal("10000"), new BigDecimal("1000000"), new BigDecimal("0.19"))));

		assertThat(tiers).hasSize(3);
		assertThat(tiers.get(0).ceiling().value()).isEqualByComparingTo("10000");
		assertThat(tiers.get(0).rate().fraction()).isEqualByComparingTo("0");
		assertThat(tiers.get(1).ceiling().value()).isEqualByComparingTo("1000000");
		assertThat(tiers.get(1).rate().fraction()).isEqualByComparingTo("0.19");
		assertThat(tiers.get(2).isUnbounded()).isTrue();
		assertThat(tiers.get(2).rate().fraction()).isEqualByComparingTo("0");
	}

	@Test
	void walletTierWithoutFloorStartsAtZeroAndStillClosesTheTop() {
		List<RateTier> tiers = InstrumentMapper.rateTiers(
				List.of(new WalletTier(null, new BigDecimal("1000000"), new BigDecimal("0.20"))));

		assertThat(tiers).hasSize(2);
		assertThat(tiers.get(0).ceiling().value()).isEqualByComparingTo("1000000");
		assertThat(tiers.get(0).rate().fraction()).isEqualByComparingTo("0.20");
		assertThat(tiers.get(1).isUnbounded()).isTrue();
		assertThat(tiers.get(1).rate().fraction()).isEqualByComparingTo("0");
	}

	@Test
	void walletTierGapBecomesAZeroBracket() {
		List<RateTier> tiers = InstrumentMapper.rateTiers(List.of(
				new WalletTier(new BigDecimal("0"), new BigDecimal("500000"), new BigDecimal("0.30")),
				new WalletTier(new BigDecimal("800000"), null, new BigDecimal("0.10"))));

		assertThat(tiers).hasSize(3);
		assertThat(tiers.get(0).ceiling().value()).isEqualByComparingTo("500000");
		assertThat(tiers.get(0).rate().fraction()).isEqualByComparingTo("0.30");
		assertThat(tiers.get(1).ceiling().value()).isEqualByComparingTo("800000");
		assertThat(tiers.get(1).rate().fraction()).isEqualByComparingTo("0");
		assertThat(tiers.get(2).isUnbounded()).isTrue();
		assertThat(tiers.get(2).rate().fraction()).isEqualByComparingTo("0.10");
	}

	@Test
	void walletUnboundedTierBecomesTheOnlyBracket() {
		List<RateTier> tiers = InstrumentMapper.rateTiers(
				List.of(new WalletTier(null, null, new BigDecimal("0.19"))));

		assertThat(tiers).hasSize(1);
		assertThat(tiers.get(0).isUnbounded()).isTrue();
		assertThat(tiers.get(0).rate().fraction()).isEqualByComparingTo("0.19");
	}

	@Test
	void walletConditionsKeepTheirLabelAndBrackets() {
		String label = "Si acumulás $50.000 en ciertas operaciones este mes";
		List<ConditionalRate> conditions = InstrumentMapper.conditionalRates(List.of(
				new WalletCondition(label, List.of(new WalletTier(null, null, new BigDecimal("0.24"))))));

		assertThat(conditions).hasSize(1);
		assertThat(conditions.get(0).condition()).isEqualTo(label);
		assertThat(conditions.get(0).tiers()).hasSize(1);
		assertThat(conditions.get(0).tiers().get(0).isUnbounded()).isTrue();
		assertThat(conditions.get(0).tiers().get(0).rate().fraction()).isEqualByComparingTo("0.24");
	}

	@Test
	void apyBecomesTheTnaEquivalentForDailyCompounding() {
		AnnualRate tna = InstrumentMapper.apyToTna(new BigDecimal("0.175"));

		// ((1 + 0.175)^(1/365) − 1) · 365 = 0.1613037794401529...
		assertThat(tna.fraction().setScale(12, RoundingMode.HALF_EVEN))
				.isEqualByComparingTo("0.161303779440");
		// Daily compounding at that TNA over a year reproduces the APY.
		assertThat(tna.dailyCompoundingGrowth(365).setScale(12, RoundingMode.HALF_EVEN))
				.isEqualByComparingTo("1.175000000000");
	}

	@Test
	void termBandIsChosenByDayAndAmountWindows() {
		List<TermRate> terms = List.of(
				new TermRate(30, 60, new BigDecimal("1000000"), null, new BigDecimal("0.30")),
				new TermRate(90, 365, null, null, new BigDecimal("0.28")));

		assertThat(InstrumentMapper.matchingTermTna(terms, 30, Money.ars("2000000")))
				.contains(new BigDecimal("0.30"));
		assertThat(InstrumentMapper.matchingTermTna(terms, 60, Money.ars("2000000")))
				.contains(new BigDecimal("0.30"));
		assertThat(InstrumentMapper.matchingTermTna(terms, 61, Money.ars("2000000"))).isEmpty();
		assertThat(InstrumentMapper.matchingTermTna(terms, 90, Money.ars("500000")))
				.contains(new BigDecimal("0.28"));
		assertThat(InstrumentMapper.matchingTermTna(List.of(new TermRate(30, null, null, null, null)), 30,
				Money.ars("2000000"))).isEmpty();
	}
}
