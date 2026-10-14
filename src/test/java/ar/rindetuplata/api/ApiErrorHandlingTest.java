package ar.rindetuplata.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.rindetuplata.source.plazofijo.ArgentinaDatosPlazoFijoAdapter;
import ar.rindetuplata.source.plazofijo.BankPlazoFijoRate;
import ar.rindetuplata.store.BankPlazoFijoWriter;
import ar.rindetuplata.store.InstrumentKind;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * T5b rules for the shared error handling (T5c reuses it): every failure is an RFC 9457 {@code ProblemDetail}
 * ({@code application/problem+json}) whose {@code status}, {@code title}, {@code detail} and {@code instance} are
 * set and whose {@code parameter} names the offending request parameter. Spring flattens the problem's properties
 * onto the body, so the parameter is a top-level field. 400 for a type mismatch, an invalid value, a bad date range
 * and a range over the maximum; 404 for an unknown instrument.
 */
class ApiErrorHandlingTest extends ApiIntegrationTest {

	private static final String PROVINCIA = "BANCO DE LA PROVINCIA DE BUENOS AIRES";

	@Test
	void unknownInstrumentIsANotFoundProblem() throws Exception {
		expectProblem(mockMvc.perform(get("/api/v1/instruments/{id}/history", 999999L)), 404, "Not Found", "id",
				"999999", "/api/v1/instruments/999999/history");
	}

	@Test
	void anInvalidKindIsABadRequestNamingTheParameter() throws Exception {
		expectProblem(mockMvc.perform(get("/api/v1/instruments").param("kind", "NOPE")), 400, "Bad Request", "kind",
				"NOPE", "/api/v1/instruments");
	}

	@Test
	void aDateThatIsNotADateIsABadRequestNamingTheParameter() throws Exception {
		seedAll();

		expectProblem(mockMvc.perform(get("/api/v1/instruments/{id}/history", bankInstrumentId()).param("from", "ayer")),
				400, "Bad Request", "from", "ayer", null);
	}

	@Test
	void fromAfterToIsABadRequest() throws Exception {
		seedAll();

		expectProblem(mockMvc.perform(get("/api/v1/instruments/{id}/history", bankInstrumentId())
				.param("from", "2026-10-10").param("to", "2026-10-01")), 400, "Bad Request", "from", "from", null);
	}

	@Test
	void aRangeLongerThan366DaysIsABadRequest() throws Exception {
		seedAll();

		expectProblem(mockMvc.perform(get("/api/v1/instruments/{id}/history", bankInstrumentId())
				.param("from", "2025-10-19").param("to", "2026-10-20")), 400, "Bad Request", "to", "366", null);
	}

	private void expectProblem(ResultActions actions, int httpStatus, String title, String parameter,
			String detailFragment, String instance) throws Exception {
		actions.andExpect(status().is(httpStatus))
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.status").value(httpStatus))
				.andExpect(jsonPath("$.title").value(title))
				.andExpect(jsonPath("$.type").doesNotExist())
				.andExpect(jsonPath("$.detail").value(containsString(detailFragment)))
				.andExpect(jsonPath("$.parameter").value(parameter));
		if (instance != null) {
			actions.andExpect(jsonPath("$.instance").value(instance));
		}
	}

	private long bankInstrumentId() {
		BankPlazoFijoRate rate = ApiFixtures.banks().stream()
				.filter(bank -> PROVINCIA.equals(bank.bankName())).findFirst().orElseThrow();
		return reader.instrument(InstrumentKind.PLAZO_FIJO_BANK, ArgentinaDatosPlazoFijoAdapter.SOURCE.id(),
				BankPlazoFijoWriter.naturalKey(rate)).orElseThrow().id();
	}
}
