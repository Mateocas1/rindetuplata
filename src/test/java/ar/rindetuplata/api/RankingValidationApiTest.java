package ar.rindetuplata.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * T5c rules for input validation: every invalid request is an RFC 9457 {@code ProblemDetail} with status 400 whose
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
	void aMissingInstrumentIdIsABadRequestNamingInstrumentId() throws Exception {
		expectBadRequest(mockMvc.perform(get("/api/v1/simulations")), "instrumentId");
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
