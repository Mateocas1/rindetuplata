package ar.rindetuplata.alert;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The alert-rules API takes a {@code type} next to today's THRESHOLD. A request without type is the
 * old threshold rule; each type accepts exactly its own fields, and a field that does not belong to the type, a
 * missing required one or an instrument of the wrong kind is a 400 ProblemDetail naming that field and leaving
 * the stored rules untouched.
 */
class AlertTypesApiTest extends AlertApiIntegrationTest {

	@Autowired
	JdbcClient jdbc;

	@Test
	void aRequestWithoutTypeIsAThresholdRule() throws Exception {
		Cookie session = login(USER_EMAIL);

		postAlert(session, "{\"kind\":\"WALLET\",\"thresholdPercent\":25}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("THRESHOLD"))
			.andExpect(jsonPath("$.kind").value("WALLET"))
			.andExpect(jsonPath("$.thresholdPercent").value(25.00));

		listAlerts(session).andExpect(jsonPath("$[0].type").value("THRESHOLD"));
		assertThat(storedTypes()).containsExactly("THRESHOLD");
	}

	@Test
	void anExplicitThresholdTypeIsReadCaseInsensitively() throws Exception {
		Cookie session = login(USER_EMAIL);

		postAlert(session, "{\"type\":\" threshold \",\"thresholdPercent\":25}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("THRESHOLD"));
	}

	@Test
	void bestChangedTakesAnOptionalKindAndNothingElse() throws Exception {
		Cookie session = login(USER_EMAIL);

		postAlert(session, "{\"type\":\"BEST_CHANGED\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("BEST_CHANGED"))
			.andExpect(jsonPath("$.kind").value(nullValue()))
			.andExpect(jsonPath("$.instrumentId").value(nullValue()))
			.andExpect(jsonPath("$.thresholdPercent").value(nullValue()))
			.andExpect(jsonPath("$.active").value(true));
		postAlert(session, "{\"type\":\"BEST_CHANGED\",\"kind\":\"FUND\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.kind").value("FUND"));

		listAlerts(session).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].type").value("BEST_CHANGED"))
			.andExpect(jsonPath("$[1].kind").value("FUND"));
	}

	@Test
	void rateDropTakesABankOrWalletInstrumentAndDerivesItsKind() throws Exception {
		long wallet = seedWallet("test-type-api", "wallet-a", "Wallet A", "0.25", CAPTURE);
		Cookie session = login(USER_EMAIL);

		postAlert(session, "{\"type\":\"RATE_DROP\",\"instrumentId\":%d}".formatted(wallet))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("RATE_DROP"))
			.andExpect(jsonPath("$.instrumentId").value((int) wallet))
			.andExpect(jsonPath("$.kind").value("WALLET"))
			.andExpect(jsonPath("$.thresholdPercent").value(nullValue()));
		postAlert(session, "{\"type\":\"RATE_DROP\",\"kind\":\"WALLET\",\"instrumentId\":%d}".formatted(wallet))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.kind").value("WALLET"));
	}

	@Test
	void weeklyDigestTakesAnOptionalKindAndNothingElse() throws Exception {
		Cookie session = login(USER_EMAIL);

		postAlert(session, "{\"type\":\"WEEKLY_DIGEST\",\"kind\":\"CRYPTO\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("WEEKLY_DIGEST"))
			.andExpect(jsonPath("$.kind").value("CRYPTO"))
			.andExpect(jsonPath("$.instrumentId").value(nullValue()))
			.andExpect(jsonPath("$.thresholdPercent").value(nullValue()));
	}

	@Test
	void anUnknownTypeIsBadRequestNamingTheTypeField() throws Exception {
		Cookie session = login(USER_EMAIL);

		rejected(postAlert(session, "{\"type\":\"LOUD\",\"thresholdPercent\":25}"), "type", "LOUD");
		postAlert(session, "{\"type\":\"LOUD\",\"thresholdPercent\":25}")
			.andExpect(jsonPath("$.detail").value(containsString("THRESHOLD")))
			.andExpect(jsonPath("$.detail").value(containsString("BEST_CHANGED")))
			.andExpect(jsonPath("$.detail").value(containsString("RATE_DROP")))
			.andExpect(jsonPath("$.detail").value(containsString("WEEKLY_DIGEST")));
		assertThat(storedTypes()).isEmpty();
	}

	@Test
	void bestChangedAndWeeklyDigestRefuseAThresholdAndAnInstrument() throws Exception {
		long wallet = seedWallet("test-type-api-b", "wallet-b", "Wallet B", "0.25", CAPTURE);
		Cookie session = login(USER_EMAIL);

		for (String type : List.of("BEST_CHANGED", "WEEKLY_DIGEST")) {
			rejected(postAlert(session, "{\"type\":\"%s\",\"thresholdPercent\":25}".formatted(type)),
					"thresholdPercent", type);
			rejected(postAlert(session, "{\"type\":\"%s\",\"instrumentId\":%d}".formatted(type, wallet)),
					"instrumentId", type);
			rejected(postAlert(session, "{\"type\":\"%s\",\"kind\":\"BILLETERA\"}".formatted(type)), "kind",
					"BILLETERA");
		}
		assertThat(storedTypes()).isEmpty();
	}

	@Test
	void rateDropNeedsAKnownBankOrWalletInstrument() throws Exception {
		long wallet = seedWallet("test-type-api-c", "wallet-c", "Wallet C", "0.25", CAPTURE);
		long crypto = seedCrypto("test-type-api-c", "platform-c", "0.20", CAPTURE);
		Cookie session = login(USER_EMAIL);

		rejected(postAlert(session, "{\"type\":\"RATE_DROP\"}"), "instrumentId", "required");
		rejected(postAlert(session, "{\"type\":\"RATE_DROP\",\"instrumentId\":999999}"), "instrumentId", "999999");
		rejected(postAlert(session, "{\"type\":\"RATE_DROP\",\"instrumentId\":%d}".formatted(crypto)),
				"instrumentId", "PLAZO_FIJO or WALLET");
		rejected(postAlert(session, "{\"type\":\"RATE_DROP\",\"instrumentId\":%d,\"thresholdPercent\":25}"
				.formatted(wallet)), "thresholdPercent", "RATE_DROP");
		rejected(postAlert(session, "{\"type\":\"RATE_DROP\",\"kind\":\"CRYPTO\",\"instrumentId\":%d}"
				.formatted(wallet)), "kind", "WALLET");
		assertThat(storedTypes()).isEmpty();
	}

	@Test
	void aThresholdRuleStillNeedsItsThreshold() throws Exception {
		Cookie session = login(USER_EMAIL);

		rejected(postAlert(session, "{\"type\":\"THRESHOLD\"}"), "thresholdPercent", "required");
		assertThat(storedTypes()).isEmpty();
	}

	@Test
	void theCapOfTwentyActiveRulesCountsEveryType() throws Exception {
		Cookie session = login(USER_EMAIL);
		for (int rule = 0; rule < MAX_ACTIVE_RULES; rule++) {
			String body = switch (rule % 3) {
				case 0 -> "{\"thresholdPercent\":25}";
				case 1 -> "{\"type\":\"BEST_CHANGED\"}";
				default -> "{\"type\":\"WEEKLY_DIGEST\"}";
			};
			postAlert(session, body).andExpect(status().isCreated());
		}

		postAlert(session, "{\"type\":\"WEEKLY_DIGEST\"}")
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value(containsString(String.valueOf(MAX_ACTIVE_RULES))))
			.andExpect(jsonPath("$.parameter").doesNotExist());

		listAlerts(session).andExpect(jsonPath("$", hasSize(MAX_ACTIVE_RULES)));
	}

	@Test
	void deletingARuleOfAnyTypeRemovesItsState() throws Exception {
		Cookie session = login(USER_EMAIL);
		String created = postAlert(session, "{\"type\":\"WEEKLY_DIGEST\"}").andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		long id = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.id")).longValue();
		jdbc.sql("""
				insert into alert_digest (rule_id, week_start, sent_for, top_key, top_name)
				values (:id, date '2026-10-12', date '2026-10-14', 'id:1', 'Wallet')
				""").param("id", id).update();

		deleteAlert(session, id).andExpect(status().isNoContent());

		assertThat(jdbc.sql("select count(*) from alert_digest").query(Long.class).single()).isZero();
	}

	/** The 400 ProblemDetail: its status, media type, the field it names and a fragment of its detail. */
	private static void rejected(ResultActions actions, String parameter, String detailFragment) throws Exception {
		actions.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.parameter").value(parameter))
			.andExpect(jsonPath("$.detail").value(containsString(detailFragment)));
	}

	private List<String> storedTypes() {
		return jdbc.sql("select type from alert_rule order by id").query(String.class).list();
	}

}
