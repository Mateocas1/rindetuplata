package ar.rindetuplata.api.ranking;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.rindetuplata.api.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * T1a follow-up (S1, verifier finding F1): Spring parses {@code amount} with {@code new BigDecimal(String)}, so a
 * request can send {@code 1e999999999}. The 400 message echoes the value, and its plain form would be a billion
 * characters, so a huge exponent is echoed in scientific notation while ordinary values keep their plain form.
 */
class RankingRequestTest {

	@Test
	void aHugeAmountIsRejectedWithABoundedMessage() {
		assertThatThrownBy(() -> RankingRequest.amount(new BigDecimal("1e9999999")))
			.isInstanceOf(ApiException.class)
			.hasMessage("Parameter 'amount' must be at most 1000000000000: 1E+9999999");
	}

	@Test
	void aHugeNegativeAmountIsRejectedWithABoundedMessage() {
		assertThatThrownBy(() -> RankingRequest.amount(new BigDecimal("-1e9999999")))
			.isInstanceOf(ApiException.class)
			.hasMessage("Parameter 'amount' must be greater than 0: -1E+9999999");
	}

	@Test
	void aNegativeAmountWithAHugeScaleIsRejectedWithABoundedMessage() {
		assertThatThrownBy(() -> RankingRequest.amount(new BigDecimal("-1e-9999999")))
			.isInstanceOf(ApiException.class)
			.hasMessage("Parameter 'amount' must be greater than 0: -1E-9999999");
	}

	@Test
	void anOrdinaryAmountKeepsItsPlainFormInTheMessage() {
		assertThatThrownBy(() -> RankingRequest.amount(new BigDecimal("1e30")))
			.isInstanceOf(ApiException.class)
			.hasMessage("Parameter 'amount' must be at most 1000000000000: 1000000000000000000000000000000");
	}
}
