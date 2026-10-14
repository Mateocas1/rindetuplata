package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * T6c rules of the alert-rules API (S3, S13): a logged-in user creates a rule with a percent threshold and reads
 * it back; a user only ever sees and deletes their own rules (another user's rule is a 404, never a 403 that would
 * confirm it exists); anonymous access is a 401 and a state-changing request without a CSRF token is a 403; an
 * invalid threshold, kind or instrument is a 400 naming the field; the 21st active rule of one user is refused.
 * One case also proves the percent is stored as the fraction the evaluation compares against.
 */
class AlertRulesApiTest extends AlertApiIntegrationTest {

	@Autowired
	AlertEvaluator evaluator;

	@Autowired
	JdbcClient jdbc;

	@Test
	void postCreatesARuleForTheLoggedInUserAndListsItBack() throws Exception {
		long instrument = seedWallet("test-rules", "wallet-a", "Wallet A", "0.25", CAPTURE);
		Cookie session = login(USER_EMAIL);

		String body = postAlert(session, """
				{"kind":"WALLET","instrumentId":%d,"thresholdPercent":25}
				""".formatted(instrument))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.kind").value("WALLET"))
			.andExpect(jsonPath("$.instrumentId").value((int) instrument))
			.andExpect(jsonPath("$.thresholdPercent").value(25.00))
			.andExpect(jsonPath("$.active").value(true))
			.andExpect(jsonPath("$.createdAt").isNotEmpty())
			.andExpect(content().string(containsString("\"thresholdPercent\":25.00")))
			.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(body, "$.id")).longValue();

		listAlerts(session).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value((int) id))
			.andExpect(jsonPath("$[0].kind").value("WALLET"))
			.andExpect(jsonPath("$[0].instrumentId").value((int) instrument))
			.andExpect(jsonPath("$[0].thresholdPercent").value(25.00))
			.andExpect(jsonPath("$[0].active").value(true));
	}

	@Test
	void aRuleWithoutKindOrInstrumentCoversEveryKind() throws Exception {
		Cookie session = login(USER_EMAIL);

		postAlert(session, "{\"thresholdPercent\":30}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.kind").value(nullValue()))
			.andExpect(jsonPath("$.instrumentId").value(nullValue()))
			.andExpect(jsonPath("$.thresholdPercent").value(30.00));

		listAlerts(session).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].kind").value(nullValue()));
	}

	@Test
	void getListsOnlyMyRules() throws Exception {
		Cookie mine = login(USER_EMAIL);
		Cookie theirs = login(OTHER_EMAIL);
		postAlert(mine, "{\"thresholdPercent\":25}").andExpect(status().isCreated());
		postAlert(mine, "{\"thresholdPercent\":20,\"kind\":\"WALLET\"}").andExpect(status().isCreated());
		postAlert(theirs, "{\"thresholdPercent\":19}").andExpect(status().isCreated());

		listAlerts(mine).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[*].thresholdPercent").value(org.hamcrest.Matchers.contains(25.00, 20.00)));

		listAlerts(theirs).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].thresholdPercent").value(19.00));
	}

	@Test
	void deleteOfMyRuleAnswersNoContentAndRemovesIt() throws Exception {
		Cookie session = login(USER_EMAIL);
		String created = postAlert(session, "{\"thresholdPercent\":25}").andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(created, "$.id")).longValue();

		deleteAlert(session, id).andExpect(status().isNoContent());

		listAlerts(session).andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
	}

	@Test
	void deletingAnotherUsersRuleIsNotFoundAndKeepsIt() throws Exception {
		Cookie mine = login(USER_EMAIL);
		Cookie theirs = login(OTHER_EMAIL);
		String created = postAlert(theirs, "{\"thresholdPercent\":25}").andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		long theirRule = ((Number) JsonPath.read(created, "$.id")).longValue();

		deleteAlert(mine, theirRule)
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.parameter").value("id"));

		listAlerts(theirs).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void anonymousReadsAreUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/alerts"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void aStateChangingRequestWithoutCsrfIsForbidden() throws Exception {
		Cookie session = login(USER_EMAIL);

		mockMvc.perform(post("/api/v1/alerts").cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"thresholdPercent\":25}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void aThresholdOutsideZeroToThreeHundredIsBadRequestNamingTheField() throws Exception {
		Cookie session = login(USER_EMAIL);

		expectThresholdProblem(postAlert(session, "{\"thresholdPercent\":0}"), "0");
		expectThresholdProblem(postAlert(session, "{\"thresholdPercent\":-1}"), "-1");
		expectThresholdProblem(postAlert(session, "{\"thresholdPercent\":301}"), "301");
		expectThresholdProblem(postAlert(session, "{\"kind\":\"WALLET\"}"), "required");
	}

	@Test
	void anUnknownKindIsBadRequestNamingTheField() throws Exception {
		Cookie session = login(USER_EMAIL);

		postAlert(session, "{\"kind\":\"BILLETERA\",\"thresholdPercent\":25}")
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.parameter").value("kind"))
			.andExpect(jsonPath("$.detail").value(containsString("BILLETERA")))
			.andExpect(jsonPath("$.detail").value(containsString("PLAZO_FIJO")));
	}

	@Test
	void anUnknownInstrumentIsBadRequestNamingTheField() throws Exception {
		Cookie session = login(USER_EMAIL);

		postAlert(session, "{\"instrumentId\":999999,\"thresholdPercent\":25}")
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.parameter").value("instrumentId"))
			.andExpect(jsonPath("$.detail").value(containsString("999999")));
	}

	@Test
	void theTwentyFirstActiveRuleIsRejected() throws Exception {
		Cookie session = login(USER_EMAIL);
		for (int rule = 1; rule <= MAX_ACTIVE_RULES; rule++) {
			postAlert(session, "{\"thresholdPercent\":25}").andExpect(status().isCreated());
		}

		postAlert(session, "{\"thresholdPercent\":25}")
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value(containsString(String.valueOf(MAX_ACTIVE_RULES))))
			.andExpect(jsonPath("$.parameter").doesNotExist());

		listAlerts(session).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(MAX_ACTIVE_RULES)));
	}

	/**
	 * The percent the API takes is stored as the fraction the evaluation compares against: the rule of 25% is
	 * created through the HTTP API, the day's ranking holds one wallet at 30%, and the AlertTriggered event that
	 * fires carries the threshold 0.25. The outbox row is the public artifact T6d relays.
	 */
	@Test
	void thePercentBecomesTheFractionTheEvaluationCompares() throws Exception {
		seedWallet("test-fraction", "wallet-f", "Wallet F", "0.30", CAPTURE);
		Cookie session = login(USER_EMAIL);
		postAlert(session, "{\"thresholdPercent\":25}").andExpect(status().isCreated());

		evaluator.evaluate(CAPTURE);

		assertThat(matchCount()).as("the 30% wallet is above the 25% rule").isEqualTo(1);
		assertThat(outboxCount()).isEqualTo(1);
		assertThat(thresholdPayload()).isEqualByComparingTo(new BigDecimal("0.25"));
	}

	private void expectThresholdProblem(org.springframework.test.web.servlet.ResultActions actions,
			String detailFragment) throws Exception {
		actions.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.parameter").value("thresholdPercent"))
			.andExpect(jsonPath("$.detail").value(containsString(detailFragment)));
	}

	private long matchCount() {
		return jdbc.sql("select count(*) from alert_match").query(Long.class).single();
	}

	private long outboxCount() {
		return jdbc.sql("select count(*) from outbox_event").query(Long.class).single();
	}

	private BigDecimal thresholdPayload() {
		return jdbc.sql("select (payload->>'threshold')::numeric from outbox_event").query(BigDecimal.class).single();
	}

}
