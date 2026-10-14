package ar.rindetuplata.account;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * T6a browser rules (S4, S13): the public GETs stay open to anonymous callers, a state-changing request without a
 * CSRF token is 403, logout answers 204 and ends the session, and CORS accepts preflights from the configured
 * origin only.
 */
class BrowserSecurityApiTest extends AccountIntegrationTest {

	@Test
	void publicEndpointsStayOpenForAnonymousRequests() throws Exception {
		mockMvc.perform(get("/api/v1/ranking")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/instruments")).andExpect(status().isOk());
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}

	@Test
	void aStateChangingRequestWithoutCsrfIsForbidden() throws Exception {
		mockMvc.perform(post("/api/v1/auth/magic-link")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + USER_EMAIL + "\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void logoutAnswersNoContentAndEndsTheSession() throws Exception {
		Cookie session = loginNewSession(USER_EMAIL);

		mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(session))
				.andExpect(status().isNoContent());

		expectProblem(mockMvc.perform(get("/api/v1/me").cookie(session)), 401, "Unauthorized");
	}

	@Test
	void aPreflightFromAnAllowedOriginIsAccepted() throws Exception {
		mockMvc.perform(options("/api/v1/ranking")
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
	}

	@Test
	void aPreflightFromAnotherOriginIsRejected() throws Exception {
		mockMvc.perform(options("/api/v1/ranking")
				.header(HttpHeaders.ORIGIN, "http://evil.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isForbidden());
	}
}
