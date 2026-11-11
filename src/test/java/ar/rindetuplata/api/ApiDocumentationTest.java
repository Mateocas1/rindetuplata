package ar.rindetuplata.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * Rule: {@code GET /v3/api-docs} answers 200 with the published document, titled "rindetuplata API", version "v1",
 * carrying the informational non-advice disclaimer, and listing every public path (instruments and
 * history, ranking and simulations).
 */
class ApiDocumentationTest extends ApiIntegrationTest {

	@Test
	void apiDocsAreServedWithTitleVersionDisclaimerAndPaths() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openapi").exists())
				.andExpect(jsonPath("$.info.title").value("rindetuplata API"))
				.andExpect(jsonPath("$.info.version").value("v1"))
				.andExpect(jsonPath("$.info.description").value(containsString("not financial advice")))
				.andExpect(jsonPath("$.paths['/api/v1/instruments']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/instruments/{id}/history']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/ranking']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/simulations']").exists());
	}

	@Test
	void theMepBreakEvenIsDocumentedOnItsSchemasAndOnBothEndpoints() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components.schemas.BreakEvenMepView.description")
						.value(containsString("(1 + nominalReturn) * sell / buy - 1")))
				.andExpect(jsonPath("$.components.schemas.BreakEvenMepView.properties.devaluation.description")
						.value(containsString("fraction")))
				.andExpect(jsonPath("$.components.schemas.BreakEvenMepView.properties.price.description")
						.value(containsString("break-even")))
				.andExpect(jsonPath("$.components.schemas.MepQuoteView.description")
						.value(containsString("latest stored")))
				.andExpect(jsonPath("$.components.schemas.MepQuoteView.properties.updatedAt.description")
						.value(containsString("source reports")))
				.andExpect(jsonPath("$.components.schemas.RankedEntryView.properties.breakEvenMep").exists())
				.andExpect(jsonPath("$.components.schemas.RankingResponse.properties.dollarMep").exists())
				.andExpect(jsonPath("$.components.schemas.SimulationResponse.properties.dollarMep").exists())
				.andExpect(jsonPath("$.paths['/api/v1/ranking'].get.description").value(containsString("dollarMep")))
				.andExpect(jsonPath("$.paths['/api/v1/simulations'].get.description")
						.value(containsString("dollarMep")));
	}
}
