package ar.rindetuplata.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.ValidationFlag;
import ar.rindetuplata.source.plazofijo.ArgentinaDatosPlazoFijoAdapter;
import ar.rindetuplata.source.plazofijo.BankPlazoFijoRate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * T6b bank entity pages, through HTTP: {@code GET /api/v1/entities/banks} and {@code /{slug}}. "Today" is
 * 2026-10-20 (the frozen clock), so the 90-day window is 2026-07-23..2026-10-20 inclusive. The properties are the
 * ones of {@link ApiRateLimitApiTest} on purpose: the same cached context serves both, and the rate limit of the
 * new path can be proven over real HTTP. Every request but the limit test carries the build token, so the three
 * tokens of an address are never spent by the data assertions.
 */
@TestPropertySource(properties = {
		"rindetuplata.api.rate-limit.capacity=3",
		"rindetuplata.api.rate-limit.refill-per-second=0.5",
		"rindetuplata.api.rate-limit.bypass-token=build-secret",
		"rindetuplata.web.allowed-origins=http://localhost:4321"
})
class BankEntityApiTest extends ApiIntegrationTest {

	private static final String LIST = "/api/v1/entities/banks";

	private ResultActions read(String path) throws Exception {
		return mockMvc.perform(get(path).header("X-Build-Token", "build-secret"));
	}

	private void capture(String date, BankPlazoFijoRate... rates) {
		bankWriter.write(List.of(rates), LocalDate.parse(date));
	}

	private static BankPlazoFijoRate bank(String date, String name, String nonClient, String client) {
		return bank(date, name, nonClient, client, List.of());
	}

	private static BankPlazoFijoRate invalidBank(String date, String name, String nonClient, String client) {
		return bank(date, name, nonClient, client, List.of(new ValidationFlag(FlagCode.INVALID_QUOTE,
				Severity.INVALID, "rate is not credible")));
	}

	private static BankPlazoFijoRate bank(String date, String name, String nonClient, String client,
			List<ValidationFlag> flags) {
		return bank(ArgentinaDatosPlazoFijoAdapter.SOURCE, date, name, nonClient, client, flags);
	}

	private static BankPlazoFijoRate bank(SourceRef source, String date, String name, String nonClient,
			String client, List<ValidationFlag> flags) {
		LocalDate day = LocalDate.parse(date);
		return new BankPlazoFijoRate(source, name,
				client == null ? null : new BigDecimal(client), nonClient == null ? null : new BigDecimal(nonClient),
				List.of(), day, day.atStartOfDay().toInstant(ZoneOffset.UTC), Confidence.AGGREGATOR_AUTOMATIC, flags);
	}

	@Test
	void theListHasOneRowPerBankWithTodaysRatesAndDatesInSlugOrder() throws Exception {
		capture("2026-10-13", bank("2026-10-13", "BANCO MACRO S.A.", "0.30", "0.35"));
		capture("2026-10-14", bank("2026-10-14", "BANCO MACRO S.A.", "0.31", "0.36"),
				bank("2026-10-14", "BANCO DE LA NACION ARGENTINA", "0.25", null));

		read(LIST)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].slug").value("banco-de-la-nacion-argentina"))
				.andExpect(jsonPath("$[0].name").value("BANCO DE LA NACION ARGENTINA"))
				.andExpect(jsonPath("$[0].sourceId").value("argentinadatos-plazo-fijo"))
				.andExpect(jsonPath("$[0].nonClientTna").value(0.25))
				.andExpect(jsonPath("$[0].clientTna").value(nullValue()))
				.andExpect(jsonPath("$[0].captureDate").value("2026-10-14"))
				.andExpect(jsonPath("$[0].dataDate").value("2026-10-14"))
				.andExpect(jsonPath("$[1].slug").value("banco-macro-s-a"))
				.andExpect(jsonPath("$[1].nonClientTna").value(0.31))
				.andExpect(jsonPath("$[1].clientTna").value(0.36))
				.andExpect(jsonPath("$[1].captureDate").value("2026-10-14"))
				.andExpect(content().string(containsString("\"nonClientTna\":0.310000")))
				.andExpect(content().string(containsString("\"clientTna\":0.360000")));
	}

	@Test
	void theListSkipsAnInvalidLatestSnapshotAndFallsBackToTheLastValidOne() throws Exception {
		capture("2026-10-13", bank("2026-10-13", "BANCO UNO", "0.30", "0.33"));
		capture("2026-10-14", invalidBank("2026-10-14", "BANCO UNO", "0.99", "0.99"));

		read(LIST)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].nonClientTna").value(0.30))
				.andExpect(jsonPath("$[0].captureDate").value("2026-10-13"));
	}

	@Test
	void aBankNotCapturedOnTheNewestDayOfItsSourceIsListedAsNotCurrentWithItsOwnCaptureDate() throws Exception {
		capture("2026-10-18", bank("2026-10-18", "BANCO DOS", "0.31", null));
		capture("2026-10-20", bank("2026-10-20", "BANCO UNO", "0.30", "0.33"));

		read(LIST)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].slug").value("banco-dos"))
				.andExpect(jsonPath("$[0].current").value(false))
				.andExpect(jsonPath("$[0].captureDate").value("2026-10-18"))
				.andExpect(jsonPath("$[0].nonClientTna").value(0.31))
				.andExpect(jsonPath("$[1].slug").value("banco-uno"))
				.andExpect(jsonPath("$[1].current").value(true))
				.andExpect(jsonPath("$[1].captureDate").value("2026-10-20"));

		// The bank page stays: same rate, same series, only flagged.
		read(LIST + "/banco-dos")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.current").value(false))
				.andExpect(jsonPath("$.captureDate").value("2026-10-18"))
				.andExpect(jsonPath("$.series[0].captureDate").value("2026-10-18"));
		read(LIST + "/banco-uno").andExpect(status().isOk()).andExpect(jsonPath("$.current").value(true));
	}

	@Test
	void currentIsRelativeToTheNewestCaptureOfTheSourceNotToToday() throws Exception {
		capture("2026-10-14", bank("2026-10-14", "BANCO UNO", "0.30", "0.33"),
				bank("2026-10-14", "BANCO DOS", "0.31", null));

		read(LIST)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].current").value(true))
				.andExpect(jsonPath("$[1].current").value(true));
		read(LIST + "/banco-uno").andExpect(jsonPath("$.current").value(true));
	}

	@Test
	void eachSourceHasItsOwnNewestCapture() throws Exception {
		SourceRef other = new SourceRef("other-plazo-fijo", "Other", false);
		capture("2026-10-20", bank("2026-10-20", "BANCO UNO", "0.30", "0.33"));
		bankWriter.write(List.of(bank(other, "2026-10-17", "BANCO DOS", "0.31", null, List.of())),
				LocalDate.parse("2026-10-17"));

		read(LIST)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].slug").value("banco-dos"))
				.andExpect(jsonPath("$[0].current").value(true))
				.andExpect(jsonPath("$[1].slug").value("banco-uno"))
				.andExpect(jsonPath("$[1].current").value(true));
	}

	@Test
	void currentIsJudgedOnTheValidSnapshotActuallyShown() throws Exception {
		capture("2026-10-13", bank("2026-10-13", "BANCO UNO", "0.30", "0.33"));
		capture("2026-10-14", invalidBank("2026-10-14", "BANCO UNO", "0.99", "0.99"),
				bank("2026-10-14", "BANCO DOS", "0.31", null));

		read(LIST)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[1].slug").value("banco-uno"))
				.andExpect(jsonPath("$[1].captureDate").value("2026-10-13"))
				.andExpect(jsonPath("$[1].current").value(false))
				.andExpect(jsonPath("$[0].slug").value("banco-dos"))
				.andExpect(jsonPath("$[0].current").value(true));
		read(LIST + "/banco-uno").andExpect(jsonPath("$.current").value(false))
				.andExpect(jsonPath("$.captureDate").value("2026-10-13"));
	}

	@Test
	void aFutureCaptureDoesNotMakeEveryoneElseNotCurrent() throws Exception {
		capture("2026-10-19", bank("2026-10-19", "BANCO UNO", "0.30", "0.33"));
		capture("2026-10-25", bank("2026-10-25", "BANCO DOS", "0.31", null));

		read(LIST)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].slug").value("banco-uno"))
				.andExpect(jsonPath("$[0].current").value(true));
	}

	@Test
	void theListIsEmptyWithoutBanks() throws Exception {
		read(LIST).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void aBankPageHasTodaysRatesTheWindowAndTheNinetyDaySeriesOldestFirst() throws Exception {
		capture("2026-10-12", bank("2026-10-12", "BANCO MACRO S.A.", "0.30", "0.35"));
		capture("2026-10-14", bank("2026-10-14", "BANCO MACRO S.A.", "0.31", "0.36"));

		read(LIST + "/banco-macro-s-a")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value("banco-macro-s-a"))
				.andExpect(jsonPath("$.name").value("BANCO MACRO S.A."))
				.andExpect(jsonPath("$.instrumentId").exists())
				.andExpect(jsonPath("$.sourceId").value("argentinadatos-plazo-fijo"))
				.andExpect(jsonPath("$.nonClientTna").value(0.31))
				.andExpect(jsonPath("$.clientTna").value(0.36))
				.andExpect(jsonPath("$.captureDate").value("2026-10-14"))
				.andExpect(jsonPath("$.dataDate").value("2026-10-14"))
				.andExpect(jsonPath("$.from").value("2026-07-23"))
				.andExpect(jsonPath("$.to").value("2026-10-20"))
				.andExpect(jsonPath("$.series", hasSize(2)))
				.andExpect(jsonPath("$.series[0].captureDate").value("2026-10-12"))
				.andExpect(jsonPath("$.series[0].nonClientTna").value(0.30))
				.andExpect(jsonPath("$.series[1].captureDate").value("2026-10-14"))
				.andExpect(jsonPath("$.series[1].nonClientTna").value(0.31))
				.andExpect(content().string(containsString("\"nonClientTna\":0.300000")));
	}

	@Test
	void instrumentIdMatchesTheInstrumentEndpointsId() throws Exception {
		capture("2026-10-14", bank("2026-10-14", "BANCO MACRO S.A.", "0.31", "0.36"));
		String id = jdbc.sql("select id::text from instrument where natural_key = 'BANCO MACRO S.A.'")
				.query(String.class).single();

		read(LIST + "/banco-macro-s-a").andExpect(jsonPath("$.instrumentId").value(id));
		read("/api/v1/instruments/" + id + "/history").andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("BANCO MACRO S.A."));
	}

	@Test
	void bestDaysCountsTiesForEachBankAndObservedDaysLeavesOutTheDaysAbankWasNotSeen() throws Exception {
		// d1: A best. d2: A and B tie. d3: A has no valid snapshot (an INVALID 0.99 must not win), B best.
		// d4: C best. D only quotes a client rate, so it is never observed and never best.
		capture("2026-10-10", bank("2026-10-10", "A", "0.30", null), bank("2026-10-10", "B", "0.25", null),
				bank("2026-10-10", "C", "0.20", null), bank("2026-10-10", "D", null, "0.50"));
		capture("2026-10-11", bank("2026-10-11", "A", "0.30", null), bank("2026-10-11", "B", "0.30", null),
				bank("2026-10-11", "C", "0.20", null));
		capture("2026-10-12", invalidBank("2026-10-12", "A", "0.99", null), bank("2026-10-12", "B", "0.28", null),
				bank("2026-10-12", "C", "0.27", null));
		capture("2026-10-13", bank("2026-10-13", "A", "0.26", null), bank("2026-10-13", "B", "0.26", null),
				bank("2026-10-13", "C", "0.27", null));

		read(LIST + "/a").andExpect(status().isOk())
				.andExpect(jsonPath("$.bestDays").value(2)).andExpect(jsonPath("$.observedDays").value(3))
				.andExpect(jsonPath("$.series", hasSize(3)));
		read(LIST + "/b").andExpect(status().isOk())
				.andExpect(jsonPath("$.bestDays").value(2)).andExpect(jsonPath("$.observedDays").value(4));
		read(LIST + "/c").andExpect(status().isOk())
				.andExpect(jsonPath("$.bestDays").value(1)).andExpect(jsonPath("$.observedDays").value(4));
		read(LIST + "/d").andExpect(status().isOk())
				.andExpect(jsonPath("$.nonClientTna").value(nullValue()))
				.andExpect(jsonPath("$.clientTna").value(0.50))
				.andExpect(jsonPath("$.bestDays").value(0)).andExpect(jsonPath("$.observedDays").value(0))
				.andExpect(jsonPath("$.series", hasSize(0)));
	}

	@Test
	void theWindowIsTheNinetyDaysEndingTodayInclusiveAndIgnoresFutureAndOlderCaptures() throws Exception {
		// 2026-07-22 is the 91st day back (out), 2026-07-23 the 90th (in), 2026-10-20 is today (in), 10-21 is out.
		capture("2026-07-22", bank("2026-07-22", "W", "0.99", null), bank("2026-07-22", "X", "0.10", null));
		capture("2026-07-23", bank("2026-07-23", "W", "0.40", null), bank("2026-07-23", "X", "0.50", null));
		capture("2026-10-20", bank("2026-10-20", "W", "0.41", null));
		capture("2026-10-21", bank("2026-10-21", "W", "0.99", null), bank("2026-10-21", "X", "0.60", null));

		read(LIST + "/w").andExpect(status().isOk())
				.andExpect(jsonPath("$.nonClientTna").value(0.41))
				.andExpect(jsonPath("$.captureDate").value("2026-10-20"))
				.andExpect(jsonPath("$.series[*].captureDate", contains("2026-07-23", "2026-10-20")))
				.andExpect(jsonPath("$.bestDays").value(1)).andExpect(jsonPath("$.observedDays").value(2));
		read(LIST + "/x").andExpect(status().isOk())
				.andExpect(jsonPath("$.nonClientTna").value(0.50))
				.andExpect(jsonPath("$.bestDays").value(1)).andExpect(jsonPath("$.observedDays").value(1));
	}

	@Test
	void banksWhoseNamesNormalizeAlikeGetDistinctStableSlugsInInstrumentIdOrder() throws Exception {
		capture("2026-10-14", bank("2026-10-14", "Banco Ñ", "0.20", null), bank("2026-10-14", "BANCO N", "0.22", null),
				bank("2026-10-14", "Banco N 2", "0.24", null));

		read(LIST)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].slug", contains("banco-n", "banco-n-2", "banco-n-2-2")));
		read(LIST + "/banco-n").andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Banco Ñ"));
		read(LIST + "/banco-n-2").andExpect(status().isOk()).andExpect(jsonPath("$.name").value("BANCO N"));
		read(LIST + "/banco-n-2-2").andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Banco N 2"));
	}

	@Test
	void anUnknownSlugIsA404ProblemDetailNamingTheSlug() throws Exception {
		capture("2026-10-14", bank("2026-10-14", "BANCO MACRO S.A.", "0.31", "0.36"));

		read(LIST + "/no-such-bank")
				.andExpect(status().isNotFound())
				.andExpect(header().string("Content-Type", containsString("application/problem+json")))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.parameter").value("slug"))
				.andExpect(jsonPath("$.detail").value(containsString("no-such-bank")));
	}

	@Test
	void anAnonymousCallerReadsTheListAndAPageButEveryOtherMethodIsRefused() throws Exception {
		capture("2026-10-14", bank("2026-10-14", "BANCO MACRO S.A.", "0.31", "0.36"));

		// No session, no token: the paths are public reads (the single API chain permits them).
		mockMvc.perform(get(LIST).with(r -> {
			r.setRemoteAddr("10.6.0.9");
			return r;
		})).andExpect(status().isOk());
		mockMvc.perform(get(LIST + "/banco-macro-s-a").with(r -> {
			r.setRemoteAddr("10.6.0.9");
			return r;
		})).andExpect(status().isOk());

		for (MockHttpServletRequestBuilder request : List.of(post(LIST), put(LIST), delete(LIST),
				post(LIST + "/banco-macro-s-a"), put(LIST + "/banco-macro-s-a"), delete(LIST + "/banco-macro-s-a"))) {
			mockMvc.perform(request)
					.andExpect(status().isForbidden())
					.andExpect(header().string("Content-Type", containsString("application/problem+json")))
					.andExpect(jsonPath("$.status").value(403));
		}
		// Even with a valid CSRF token the anonymous caller gets a client error, never a write.
		mockMvc.perform(post(LIST).with(csrf())).andExpect(status().is4xxClientError());
		mockMvc.perform(delete(LIST + "/banco-macro-s-a").with(csrf())).andExpect(status().is4xxClientError());
	}

	@Test
	void theEntityPathsShareTheReadApiRateLimit() throws Exception {
		capture("2026-10-14", bank("2026-10-14", "BANCO MACRO S.A.", "0.31", "0.36"));
		String address = "10.6.0.1";

		for (String path : List.of(LIST, LIST + "/banco-macro-s-a", LIST + "/banco-macro-s-a")) {
			mockMvc.perform(get(path).with(r -> {
				r.setRemoteAddr(address);
				return r;
			})).andExpect(status().isOk());
		}
		mockMvc.perform(get(LIST).with(r -> {
			r.setRemoteAddr(address);
			return r;
		}))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "2"))
				.andExpect(jsonPath("$.instance").value(LIST));
		// The build token (the static site build sends it) skips the limit on the new paths too.
		read(LIST).andExpect(status().isOk());
	}

	@Test
	void existingPublicEndpointsAnswerAsBefore() throws Exception {
		seedAll();

		read("/api/v1/instruments").andExpect(status().isOk()).andExpect(jsonPath("$[0].id").exists());
		read("/api/v1/ranking").andExpect(status().isOk()).andExpect(jsonPath("$.ranked").isArray());
		read("/v3/api-docs").andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/v1/entities/banks']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/entities/banks/{slug}']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/instruments']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/ranking']").exists());
	}
}
