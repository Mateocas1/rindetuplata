package ar.rindetuplata.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * Rule: {@code GET /v3/api-docs} answers 200 with the published document, titled "rindetuplata API", version "v1",
 * carrying the informational non-advice disclaimer (S8 L-G), and listing the T5b paths.
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
				.andExpect(jsonPath("$.paths['/api/v1/instruments/{id}/history']").exists());
	}
}
