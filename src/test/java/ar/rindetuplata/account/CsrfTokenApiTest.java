package ar.rindetuplata.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

/**
 * T7a browser CSRF contract (S4, S13, S14): the SPA never reads cookies, so it asks for a token at
 * {@code GET /api/v1/auth/csrf} and sends it as the {@code X-XSRF-TOKEN} header. With Spring Security's SPA CSRF
 * support the header is compared as the raw token while the {@code CsrfToken} request attribute is XOR-masked, so
 * the endpoint must publish the raw one. A successful login rotates the token, so the frontend refetches it.
 */
class CsrfTokenApiTest extends AccountIntegrationTest {

	private static final String CSRF_PATH = "/api/v1/auth/csrf";

	private static final String CSRF_HEADER = "X-XSRF-TOKEN";

	private static final String CSRF_COOKIE = "XSRF-TOKEN";

	@Autowired
	FilterChainProxy securityFilterChains;

	@Autowired
	CsrfTokenRepository csrfTokenRepository;

	/**
	 * Spring Security Test's {@code csrf()} post-processor swaps the shared {@code CsrfFilter}'s repository for its
	 * own {@code TestCsrfTokenRepository} (header {@code X-CSRF-TOKEN}, no cookie) and never restores it, so a
	 * cached context first used by a test that called {@code with(csrf())} would answer this class with that fake.
	 * This class is about the real repository, so put it back before every test.
	 */
	@BeforeEach
	void restoreTheRealCsrfTokenRepository() {
		for (SecurityFilterChain chain : securityFilterChains.getFilterChains()) {
			for (Filter filter : chain.getFilters()) {
				if (filter instanceof CsrfFilter csrfFilter) {
					ReflectionTestUtils.setField(csrfFilter, "tokenRepository", csrfTokenRepository);
				}
			}
		}
	}

	@Test
	void theCsrfEndpointReturnsTheRawTokenAndTheCookie() throws Exception {
		MvcResult result = mockMvc.perform(get(CSRF_PATH))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.headerName").value(CSRF_HEADER))
			.andExpect(jsonPath("$.token").isNotEmpty())
			.andReturn();

		String token = bodyToken(result);
		Cookie cookie = xsrfCookie(result);
		assertThat(cookie).as("the endpoint writes the XSRF-TOKEN cookie").isNotNull();
		assertThat(token).as("the body carries the raw token the header is compared against")
			.isEqualTo(cookie.getValue());
	}

	@Test
	void aPostWithThatHeaderIsAcceptedAndWithoutItIsForbidden() throws Exception {
		MvcResult result = mockMvc.perform(get(CSRF_PATH)).andExpect(status().isOk()).andReturn();
		String token = bodyToken(result);
		Cookie cookie = xsrfCookie(result);

		mockMvc.perform(post("/api/v1/auth/magic-link")
				.cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + USER_EMAIL + "\"}"))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/auth/magic-link")
				.cookie(cookie)
				.header(CSRF_HEADER, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + USER_EMAIL + "\"}"))
			.andExpect(status().isAccepted());
	}

	@Test
	void theCsrfEndpointIsPublishedInTheOpenApiDocument() throws Exception {
		String document = openApiDocument();

		assertThat(JsonPath.<String>read(document, "$.paths['" + CSRF_PATH + "'].get.summary"))
			.as("the frontend needs the csrf endpoint in the published contract").isNotBlank();
	}

	/**
	 * The chosen T7a contract: a successful login rotates the CSRF token and clears its cookie, so the SPA refetches
	 * {@code GET /api/v1/auth/csrf} (named in the login OpenAPI description) before its next state-changing request.
	 * The stale in-memory token must not pass.
	 */
	@Test
	void aLoginInvalidatesTheCsrfTokenAndTheFrontendRefetches() throws Exception {
		MvcResult first = mockMvc.perform(get(CSRF_PATH)).andExpect(status().isOk()).andReturn();
		String oldToken = bodyToken(first);
		Cookie oldCookie = xsrfCookie(first);

		mockMvc.perform(post("/api/v1/auth/magic-link")
				.cookie(oldCookie)
				.header(CSRF_HEADER, oldToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + USER_EMAIL + "\"}"))
			.andExpect(status().isAccepted());

		MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
				.cookie(oldCookie)
				.header(CSRF_HEADER, oldToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + lastMailedToken() + "\"}"))
			.andExpect(status().isOk())
			.andReturn();
		Cookie session = sessionCookie(login);

		Cookie invalidated = xsrfCookie(login);
		assertThat(invalidated).as("the login response clears the XSRF-TOKEN cookie").isNotNull();
		assertThat(invalidated.getValue()).isEmpty();

		mockMvc.perform(post("/api/v1/alerts")
				.cookie(session)
				.header(CSRF_HEADER, oldToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"thresholdPercent\":25}"))
			.andExpect(status().isForbidden());

		MvcResult refreshed = mockMvc.perform(get(CSRF_PATH)).andExpect(status().isOk()).andReturn();
		String newToken = bodyToken(refreshed);
		assertThat(newToken).as("the refetched token is a new one").isNotEqualTo(oldToken);

		mockMvc.perform(post("/api/v1/alerts")
				.cookie(session)
				.cookie(xsrfCookie(refreshed))
				.header(CSRF_HEADER, newToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"thresholdPercent\":25}"))
			.andExpect(status().isCreated());
	}

	@Test
	void theLoginOpenApiDescriptionNamesTheCsrfRefetch() throws Exception {
		String description = JsonPath.read(openApiDocument(), "$.paths['/api/v1/auth/login'].post.description");

		assertThat(description).as("the login contract tells the client how to get a fresh CSRF token")
			.contains(CSRF_PATH);
	}

	private String bodyToken(MvcResult result) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
	}

	private Cookie xsrfCookie(MvcResult result) {
		return result.getResponse().getCookie(CSRF_COOKIE);
	}

	private String openApiDocument() throws Exception {
		return mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

}
