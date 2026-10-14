package ar.rindetuplata.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * T5c rules for {@code GET /api/v1/simulations}: 200 with a {@code status} discriminator that is SIMULATED for an
 * eligible instrument (the S19 client switch included), INELIGIBLE for a term below the minimum and EXCLUDED for
 * a bank that does not offer its rate to non-clients; an unknown id is a 404 ProblemDetail naming instrumentId.
 */
class SimulationApiTest extends RankingApiIntegrationTest {

	@Test
	void anEligibleInstrumentReturnsASimulatedEntry() throws Exception {
		long id = seedBank("Banco Simulable", "0.21", "0.25");

		mockMvc.perform(get("/api/v1/simulations")
				.param("instrumentId", String.valueOf(id))
				.param("amount", "1000000")
				.param("days", "30"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SIMULATED"))
				.andExpect(jsonPath("$.entry.id").value(String.valueOf(id)))
				.andExpect(jsonPath("$.entry.kind").value("PLAZO_FIJO"))
				.andExpect(jsonPath("$.entry.name").value("Banco Simulable"))
				.andExpect(jsonPath("$.entry.rateUsed.kind").value("NON_CLIENT"))
				.andExpect(jsonPath("$.entry.rateUsed.fraction").value(0.21))
				.andExpect(jsonPath("$.entry.finalAmount").value(1017260.27))
				.andExpect(jsonPath("$.entry.earned").value(17260.27))
				.andExpect(jsonPath("$.ineligible").value(nullValue()))
				.andExpect(jsonPath("$.excluded").value(nullValue()))
				.andExpect(content().string(containsString("\"finalAmount\":1017260.27")));

		mockMvc.perform(get("/api/v1/simulations")
				.param("instrumentId", String.valueOf(id))
				.param("amount", "1000000")
				.param("days", "30")
				.param("client", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SIMULATED"))
				.andExpect(jsonPath("$.entry.rateUsed.kind").value("CLIENT"))
				.andExpect(jsonPath("$.entry.rateUsed.fraction").value(0.25))
				.andExpect(jsonPath("$.entry.finalAmount").value(1020547.95));
	}

	@Test
	void aTermBelowTheMinimumIsIneligible() throws Exception {
		long id = seedBank("Banco 30 Dias", "0.21", null);

		mockMvc.perform(get("/api/v1/simulations")
				.param("instrumentId", String.valueOf(id))
				.param("amount", "1000000")
				.param("days", "29"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("INELIGIBLE"))
				.andExpect(jsonPath("$.entry").value(nullValue()))
				.andExpect(jsonPath("$.ineligible.id").value(String.valueOf(id)))
				.andExpect(jsonPath("$.ineligible.name").value("Banco 30 Dias"))
				.andExpect(jsonPath("$.ineligible.reason").value("MINIMUM_TERM"))
				.andExpect(jsonPath("$.ineligible.detail").value("Minimum term is 30 days, requested 29"));
	}

	@Test
	void aBankNotOfferedToNonClientsIsExcluded() throws Exception {
		long id = seedBank("Banco Solo Clientes", null, "0.25");

		mockMvc.perform(get("/api/v1/simulations")
				.param("instrumentId", String.valueOf(id))
				.param("amount", "1000000")
				.param("days", "30"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("EXCLUDED"))
				.andExpect(jsonPath("$.entry").value(nullValue()))
				.andExpect(jsonPath("$.excluded.id").value(String.valueOf(id)))
				.andExpect(jsonPath("$.excluded.name").value("Banco Solo Clientes"))
				.andExpect(jsonPath("$.excluded.reason").value("NOT_OFFERED_TO_NON_CLIENTS"))
				.andExpect(jsonPath("$.excluded.flags").value(empty()));
	}

	@Test
	void anUnknownInstrumentIsANotFoundProblemNamingInstrumentId() throws Exception {
		mockMvc.perform(get("/api/v1/simulations").param("instrumentId", "999999"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.title").value("Not Found"))
				.andExpect(jsonPath("$.detail").value(containsString("999999")))
				.andExpect(jsonPath("$.parameter").value("instrumentId"));
	}
}
