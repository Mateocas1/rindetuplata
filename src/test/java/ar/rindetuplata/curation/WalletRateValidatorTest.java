package ar.rindetuplata.curation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import ar.rindetuplata.api.ApiException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * T6b unit rules for the admin write-path validation (S12): every rejected value names the field it came from, so
 * the shared advice turns it into a 400 that says which parameter to fix.
 */
class WalletRateValidatorTest {

	private static final TierRequest TIER = new TierRequest(null, new BigDecimal("1000000"), new BigDecimal("20"));

	@Test
	void aWellFormedIdAndDocumentPass() {
		assertThatCode(() -> WalletRateValidator.walletId("naranja-x")).doesNotThrowAnyException();
		assertThatCode(() -> WalletRateValidator.document(new WalletRateRequest("Naranja X", List.of(TIER),
				List.of(new ConditionRequest("Si consumís $1", List.of(TIER))), "note"))).doesNotThrowAnyException();
	}

	@Test
	void anIdOutsideThePatternIsRejected() {
		assertThat(parameterOf(() -> WalletRateValidator.walletId("NARANJA-X"))).isEqualTo("walletId");
		assertThat(parameterOf(() -> WalletRateValidator.walletId("naranja x"))).isEqualTo("walletId");
		assertThat(parameterOf(() -> WalletRateValidator.walletId("a"))).isEqualTo("walletId");
		assertThat(parameterOf(() -> WalletRateValidator.walletId("a".repeat(41)))).isEqualTo("walletId");
		assertThat(parameterOf(() -> WalletRateValidator.walletId(null))).isEqualTo("walletId");
	}

	@Test
	void aBlankNameIsRejected() {
		assertThat(parameterOf(() -> WalletRateValidator.document(
				new WalletRateRequest("  ", List.of(TIER), List.of(), null)))).isEqualTo("name");
	}

	@Test
	void anEmptyTierListIsRejected() {
		assertThat(parameterOf(() -> WalletRateValidator.document(
				new WalletRateRequest("W", List.of(), List.of(), null)))).isEqualTo("tiers");
		assertThat(parameterOf(() -> WalletRateValidator.document(
				new WalletRateRequest("W", null, List.of(), null)))).isEqualTo("tiers");
	}

	@Test
	void aTnaPercentOutsideZeroToThreeHundredIsRejected() {
		assertThat(parameterOf(() -> WalletRateValidator.document(new WalletRateRequest("W",
				List.of(new TierRequest(null, null, BigDecimal.ZERO)), List.of(), null))))
				.isEqualTo("tiers[0].tnaPercent");
		assertThat(parameterOf(() -> WalletRateValidator.document(new WalletRateRequest("W",
				List.of(new TierRequest(null, null, new BigDecimal("301"))), List.of(), null))))
				.isEqualTo("tiers[0].tnaPercent");
		assertThat(parameterOf(() -> WalletRateValidator.document(new WalletRateRequest("W",
				List.of(new TierRequest(null, null, null)), List.of(), null))))
				.isEqualTo("tiers[0].tnaPercent");
	}

	@Test
	void aNegativeFloorIsRejected() {
		assertThat(parameterOf(() -> WalletRateValidator.document(new WalletRateRequest("W",
				List.of(new TierRequest(new BigDecimal("-1"), null, new BigDecimal("20"))), List.of(), null))))
				.isEqualTo("tiers[0].floor");
	}

	@Test
	void aCeilingNotAboveTheFloorIsRejected() {
		assertThat(parameterOf(() -> WalletRateValidator.document(new WalletRateRequest("W",
				List.of(new TierRequest(new BigDecimal("1000000"), new BigDecimal("1000000"), new BigDecimal("20"))),
				List.of(), null)))).isEqualTo("tiers[0].ceiling");
	}

	@Test
	void overlappingOrUnorderedTiersAreRejected() {
		assertThat(parameterOf(() -> WalletRateValidator.document(new WalletRateRequest("W",
				List.of(TIER, new TierRequest(new BigDecimal("500000"), new BigDecimal("2000000"),
						new BigDecimal("20"))),
				List.of(), null)))).isEqualTo("tiers");
		assertThat(parameterOf(() -> WalletRateValidator.document(new WalletRateRequest("W",
				List.of(new TierRequest(new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("20")),
						new TierRequest(new BigDecimal("500"), new BigDecimal("600"), new BigDecimal("20"))),
				List.of(), null)))).isEqualTo("tiers");
	}

	@Test
	void aConditionIsValidatedToo() {
		assertThat(parameterOf(() -> WalletRateValidator.document(new WalletRateRequest("W", List.of(TIER),
				List.of(new ConditionRequest("", List.of(TIER))), null))))
				.isEqualTo("conditions[0].condition");
		assertThat(parameterOf(() -> WalletRateValidator.document(new WalletRateRequest("W", List.of(TIER),
				List.of(new ConditionRequest("Si consumís $1",
						List.of(new TierRequest(null, null, BigDecimal.ZERO)))),
				null)))).isEqualTo("conditions[0].tiers[0].tnaPercent");
	}

	private static String parameterOf(ThrowingCall call) {
		Throwable thrown = catchThrowable(call::run);
		assertThat(thrown).isInstanceOf(ApiException.class);
		return ((ApiException) thrown).parameter();
	}

	@FunctionalInterface
	private interface ThrowingCall {

		void run();
	}
}
