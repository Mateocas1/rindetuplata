package ar.rindetuplata.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Rules for input validation: every invalid request is an RFC 9457 {@code ProblemDetail} with status 400 whose
 * {@code parameter} names the offending request parameter. One test per rule, including a missing required
 * parameter, which the shared advice maps from Spring's own {@code MissingServletRequestParameterException}.
 */
class RankingValidationApiTest extends RankingApiIntegrationTest {

	@Test
	void anAmountThatIsNotPositiveIsABadRequestNamingAmount() throws Exception {
		expectBadRequest(mockMvc.perform(get("/api/v1/ranking").param("amount", "0")), "amount");
	}

	@Test
	void anAmountThatIsNotANumberIsABadRequestNamingAmount() throws Exception {
		expectBadRequest(mockMvc.perform(get("/api/v1/ranking").param("amount", "un-millon")), "amount");
	}

	@Test
	void daysBelowOneIsABadRequestNamingDays() throws Exception {
		expectBadRequest(mockMvc.perform(get("/api/v1/ranking").param("days", "0")), "days");
	}

	@Test
	void aLimitBelowOneIsABadRequestNamingLimit() throws Exception {
		expectBadRequest(mockMvc.perform(get("/api/v1/ranking").param("limit", "0")), "limit");
	}

	@Test
	void aLimitAboveTwoHundredIsABadRequestNamingLimit() throws Exception {
		expectBadRequest(mockMvc.perform(get("/api/v1/ranking").param("limit", "201")), "limit");
	}

	@Test
	void anUnknownKindIsABadRequestNamingKind() throws Exception {
		expectBadRequest(mockMvc.perform(get("/api/v1/ranking").param("kind", "NOPE")), "kind");
	}

	@Test
	void aMalformedAsOfIsABadRequestNamingAsOf() throws Exception {
		expectBadRequest(mockMvc.perform(get("/api/v1/ranking").param("asOf", "ayer")), "asOf");
	}

	@Test
	void anAsOfOutsideYear1To9999IsABadRequestNamingAsOfBeforeAnyWork() throws Exception {
		for (String extreme : new String[] { "+999999999-12-31", "-999999999-01-01", "+10000-01-01", "0000-12-31" }) {
			expectBadRequest(mockMvc.perform(get("/api/v1/ranking").param("asOf", extreme)), "asOf",
					"Parameter 'asOf' must be a date between 0001-01-01 and 9999-12-31: " + extreme);
		}
	}

	@Test
	void theFirstAndLastSupportedAsOfAreStillAccepted() throws Exception {
		mockMvc.perform(get("/api/v1/ranking").param("asOf", "0001-01-01")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/ranking").param("asOf", "9999-12-31")).andExpect(status().isOk());
	}

	@Test
	void daysAboveTheCapAreABadRequestNamingDaysOnBothEndpoints() throws Exception {
		expectBadRequest(mockMvc.perform(get("/api/v1/ranking").param("days", "1826")), "days",
				"Parameter 'days' must be between 1 and 1825: 1826");
		expectBadRequest(mockMvc.perform(get("/api/v1/ranking").param("days", "2000000000")), "days",
				"Parameter 'days' must be between 1 and 1825: 2000000000");
		expectBadRequest(mockMvc.perform(get("/api/v1/simulations").param("instrumentId", "1").param("days", "1826")),
				"days", "Parameter 'days' must be between 1 and 1825: 1826");
	}

	@Test
	void theDaysCapItselfIsAccepted() throws Exception {
		long id = seedBank("Banco Tope Dias", "0.21", null);

		mockMvc.perform(get("/api/v1/ranking").param("days", "1825")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/simulations").param("instrumentId", String.valueOf(id)).param("days", "1825"))
				.andExpect(status().isOk());
	}

	@Test
	void anAmountAboveTheCapIsABadRequestNamingAmountOnBothEndpoints() throws Exception {
		expectBadRequest(mockMvc.perform(get("/api/v1/ranking").param("amount", "1000000000000.01")), "amount",
				"Parameter 'amount' must be at most 1000000000000: 1000000000000.01");
		expectBadRequest(mockMvc.perform(get("/api/v1/ranking").param("amount", "1e30")), "amount",
				"Parameter 'amount' must be at most 1000000000000: 1000000000000000000000000000000");
		expectBadRequest(mockMvc.perform(
				get("/api/v1/simulations").param("instrumentId", "1").param("amount", "1000000000001")), "amount",
				"Parameter 'amount' must be at most 1000000000000: 1000000000001");
	}

	@Test
	void theAmountCapItselfIsAccepted() throws Exception {
		long id = seedBank("Banco Tope Monto", "0.21", null);

		mockMvc.perform(get("/api/v1/ranking").param("amount", "1000000000000")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/simulations").param("instrumentId", String.valueOf(id)).param("amount",
				"1000000000000")).andExpect(status().isOk());
	}

	@Test
	void moreClientBanksThanTheCapAreABadRequestNamingClientOfCountingTheRawList() throws Exception {
		expectBadRequest(mockMvc.perform(get("/api/v1/ranking").param("clientOf", clientOf(101, false))), "clientOf",
				"Parameter 'clientOf' must have at most 100 values: 101");
		expectBadRequest(mockMvc.perform(get("/api/v1/ranking").param("clientOf", clientOf(101, true))), "clientOf",
				"Parameter 'clientOf' must have at most 100 values: 101");
	}

	@Test
	void theClientOfCapItselfIsAccepted() throws Exception {
		mockMvc.perform(get("/api/v1/ranking").param("clientOf", clientOf(100, false))).andExpect(status().isOk());
	}

	private static String clientOf(int values, boolean repeated) {
		return IntStream.rangeClosed(1, values).mapToObj(i -> Integer.toString(repeated ? 1 : i))
				.collect(Collectors.joining(","));
	}

	@Test
	void theDefaultsWithoutDaysOrAmountStillAnswer200() throws Exception {
		mockMvc.perform(get("/api/v1/ranking")).andExpect(status().isOk())
				.andExpect(jsonPath("$.request.days").value(30));
	}

	@Test
	void aMissingInstrumentIdIsABadRequestNamingInstrumentId() throws Exception {
		expectBadRequest(mockMvc.perform(get("/api/v1/simulations")), "instrumentId");
	}

	private void expectBadRequest(ResultActions actions, String parameter, String detail) throws Exception {
		expectBadRequest(actions, parameter);
		actions.andExpect(jsonPath("$.detail").value(detail));
	}

	private void expectBadRequest(ResultActions actions, String parameter) throws Exception {
		actions.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.title").value("Bad Request"))
				.andExpect(jsonPath("$.detail").value(containsString(parameter)))
				.andExpect(jsonPath("$.parameter").value(parameter));
	}
}
