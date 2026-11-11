package ar.rindetuplata.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.rindetuplata.curation.CuratedWalletRateService;
import ar.rindetuplata.curation.TierRequest;
import ar.rindetuplata.curation.WalletRateRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Rules for the two public read endpoints: a weak {@code ETag} built from the data version and the
 * normalized query, {@code Cache-Control: public, no-cache}, and a {@code 304} without ranking work for a matching
 * {@code If-None-Match}. The tag changes when the data or the query changes and not otherwise; error responses
 * carry neither. "No ranking work" is observable: a poisoned fund row makes any ranking throw, yet a matching
 * conditional request still answers 304.
 */
class ConditionalRequestApiTest extends CachedRankingSupport {

	private static final Pattern WEAK_TAG = Pattern.compile("W/\"[A-Za-z0-9_-]{22}\"");

	@Autowired
	CuratedWalletRateService curation;

	@Test
	void theRankingCarriesAWeakEtagAndPublicNoCache() throws Exception {
		seedBank("Banco A", "0.30", null);

		MockHttpServletResponse response = ranking().andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getHeader(HttpHeaders.ETAG)).matches(WEAK_TAG);
		assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("public, no-cache");
	}

	@Test
	void theTagIsTheSameForTheSameDataAndForEveryWayOfSpellingTheSameQuery() throws Exception {
		long bank = seedBank("Banco A", "0.30", "0.35");
		String base = etag(ranking());

		assertThat(etag(ranking())).isEqualTo(base);
		assertThat(etag(ranking("amount", "1000000"))).as("explicit default amount").isEqualTo(base);
		assertThat(etag(ranking("amount", "1000000.00"))).as("same amount, another scale").isEqualTo(base);
		assertThat(etag(ranking("days", "30", "limit", "50", "groupFunds", "true"))).as("explicit defaults")
				.isEqualTo(base);
		assertThat(etag(ranking("asOf", "2026-10-20"))).as("asOf resolved from the clock").isEqualTo(base);
		assertThat(etag(ranking("kind", "WALLET", "kind", "FUND", "kind", "PLAZO_FIJO", "kind", "CRYPTO")))
				.as("every kind spelled out").isEqualTo(base);
		assertThat(etag(ranking("kind", "FUND", "kind", "WALLET")))
				.isEqualTo(etag(ranking("kind", "WALLET", "kind", "FUND", "kind", "FUND")));
		assertThat(etag(ranking("clientOf", bank + ",7")))
				.isEqualTo(etag(ranking("clientOf", "7", "clientOf", String.valueOf(bank), "clientOf", "7")));
	}

	@Test
	void theTagChangesWithEveryPartOfTheQuery() throws Exception {
		long bank = seedBank("Banco A", "0.30", "0.35");
		String base = etag(ranking());

		assertThat(List.of(
				etag(ranking("amount", "1000001")),
				etag(ranking("days", "31")),
				etag(ranking("limit", "49")),
				etag(ranking("groupFunds", "false")),
				etag(ranking("kind", "PLAZO_FIJO")),
				etag(ranking("clientOf", String.valueOf(bank))),
				etag(ranking("asOf", "2026-10-19")))).doesNotContain(base).doesNotHaveDuplicates();
	}

	@Test
	void theTagChangesWhenAnIngestionRunOrACurationWriteChangesTheData() throws Exception {
		seedBank("Banco A", "0.30", null);
		String base = etag(ranking());

		recordIngestionRun();
		String afterRun = etag(ranking());
		curation.save("naranja-x", new WalletRateRequest("Naranja X",
				List.of(new TierRequest(null, new BigDecimal("1000000"), new BigDecimal("20"))), List.of(), null),
				"admin@rindetuplata.ar");
		String afterCuration = etag(ranking());

		assertThat(List.of(base, afterRun, afterCuration)).doesNotHaveDuplicates();
	}

	@Test
	void aMatchingIfNoneMatchAnswers304WithTheTagAndNoBody() throws Exception {
		seedBank("Banco A", "0.30", null);
		String tag = etag(ranking());

		MockHttpServletResponse response = rankingIfNoneMatch(tag).andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(304);
		assertThat(response.getContentAsString()).isEmpty();
		assertThat(response.getHeader(HttpHeaders.ETAG)).isEqualTo(tag);
		assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("public, no-cache");
	}

	@Test
	void theTagIsMatchedAsWeakAndAmongSeveralCandidates() throws Exception {
		seedBank("Banco A", "0.30", null);
		String tag = etag(ranking());
		String opaque = tag.substring(2);

		assertThat(statusOf(rankingIfNoneMatch("\"other\", " + tag + ", W/\"another\""))).isEqualTo(304);
		assertThat(statusOf(rankingIfNoneMatch(opaque))).as("the strong form of the same tag").isEqualTo(304);
	}

	@Test
	void aTagOfOtherDataOrAnotherQueryOrNoTagRanksNormally() throws Exception {
		seedBank("Banco A", "0.30", null);
		String tag = etag(ranking());

		assertThat(statusOf(rankingIfNoneMatch("\"stale\""))).isEqualTo(200);
		assertThat(statusOf(rankingIfNoneMatch("*"))).as("a wildcard is not treated as a match").isEqualTo(200);
		assertThat(statusOf(rankingIfNoneMatch(tag, "days", "31"))).as("a tag of another query").isEqualTo(200);
		recordIngestionRun();
		MockHttpServletResponse fresh = rankingIfNoneMatch(tag).andReturn().getResponse();
		assertThat(fresh.getStatus()).as("the data moved on").isEqualTo(200);
		assertThat(fresh.getHeader(HttpHeaders.ETAG)).isNotEqualTo(tag);
	}

	@Test
	void aMatchingIfNoneMatchDoesNoRankingWork() throws Exception {
		seedBank("Banco A", "0.30", null);
		String tag = etag(ranking("kind", "PLAZO_FIJO"));
		poisonStoredRates();

		assertThat(statusOf(rankingIfNoneMatch(tag, "kind", "PLAZO_FIJO"))).isEqualTo(304);
		assertThatThrownBy(() -> mockMvc.perform(get("/api/v1/ranking").with(fromThisClient())
				.param("kind", "PLAZO_FIJO").param("days", "31")))
				.as("the poison really breaks a ranking that reads the rates")
				.hasRootCauseInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void anInvalidRequestIsA400WithNoTagAndNoCacheHeader() throws Exception {
		MockHttpServletResponse response = mockMvc.perform(get("/api/v1/ranking").param("days", "0")
				.with(fromThisClient())).andExpect(status().isBadRequest()).andReturn().getResponse();

		assertThat(response.getHeader(HttpHeaders.ETAG)).isNull();
		assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).doesNotContain("public");
	}

	@Test
	void theSimulationCarriesAWeakEtagAndPublicNoCache() throws Exception {
		long bank = seedBank("Banco A", "0.30", null);

		MockHttpServletResponse response = simulation(bank).andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getHeader(HttpHeaders.ETAG)).matches(WEAK_TAG);
		assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("public, no-cache");
	}

	@Test
	void theSimulationTagChangesWithTheInstrumentTheTermTheAmountTheClientFlagAndTheData() throws Exception {
		long bank = seedBank("Banco A", "0.30", "0.35");
		long other = seedBank("Banco B", "0.20", null);
		String base = etag(simulation(bank));

		assertThat(etag(simulation(bank))).isEqualTo(base);
		assertThat(etag(simulation(bank, "amount", "1000000.00", "days", "30", "client", "false")))
				.as("explicit defaults").isEqualTo(base);
		recordIngestionRun();
		assertThat(List.of(
				etag(simulation(bank)),
				etag(simulation(other)),
				etag(simulation(bank, "days", "31")),
				etag(simulation(bank, "amount", "2000000")),
				etag(simulation(bank, "client", "true")))).doesNotContain(base).doesNotHaveDuplicates();
	}

	@Test
	void aMatchingIfNoneMatchOnASimulationAnswers304WithoutSimulating() throws Exception {
		long bank = seedBank("Banco A", "0.30", null);
		String tag = etag(simulation(bank));
		poisonStoredRates();
		assertThatThrownBy(() -> simulation(bank, "days", "31"))
				.as("the poison really breaks a simulation that reads the rate")
				.hasRootCauseInstanceOf(IllegalArgumentException.class);

		MockHttpServletResponse response = simulationIfNoneMatch(bank, tag).andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(304);
		assertThat(response.getContentAsString()).isEmpty();
		assertThat(response.getHeader(HttpHeaders.ETAG)).isEqualTo(tag);
		assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("public, no-cache");
	}

	@Test
	void anUnknownInstrumentIsA404WithNoTagEvenWithAnIfNoneMatch() throws Exception {
		seedBank("Banco A", "0.30", null);

		MockHttpServletResponse response = mockMvc.perform(get("/api/v1/simulations").with(fromThisClient())
				.param("instrumentId", "999999").header(HttpHeaders.IF_NONE_MATCH, "W/\"anything\""))
				.andExpect(status().isNotFound()).andReturn().getResponse();

		assertThat(response.getHeader(HttpHeaders.ETAG)).isNull();
	}

	private org.springframework.test.web.servlet.ResultActions ranking(String... params) throws Exception {
		return perform(rankingRequest(), null, params);
	}

	private org.springframework.test.web.servlet.ResultActions rankingIfNoneMatch(String ifNoneMatch, String... params)
			throws Exception {
		return perform(rankingRequest(), ifNoneMatch, params);
	}

	private org.springframework.test.web.servlet.ResultActions simulation(long id, String... params)
			throws Exception {
		MockHttpServletRequestBuilder request = get("/api/v1/simulations").with(fromThisClient())
				.param("instrumentId", String.valueOf(id));
		return perform(request, null, params);
	}

	private org.springframework.test.web.servlet.ResultActions simulationIfNoneMatch(long id, String ifNoneMatch)
			throws Exception {
		MockHttpServletRequestBuilder request = get("/api/v1/simulations").with(fromThisClient())
				.param("instrumentId", String.valueOf(id));
		return perform(request, ifNoneMatch);
	}

	private MockHttpServletRequestBuilder rankingRequest() {
		return get("/api/v1/ranking").with(fromThisClient());
	}

	private org.springframework.test.web.servlet.ResultActions perform(MockHttpServletRequestBuilder request,
			String ifNoneMatch, String... params) throws Exception {
		if (ifNoneMatch != null) {
			request.header(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
		}
		for (int i = 0; i + 1 < params.length; i += 2) {
			request.param(params[i], params[i + 1]);
		}
		return mockMvc.perform(request);
	}

	private static String etag(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
		MockHttpServletResponse response = actions.andExpect(status().isOk()).andReturn().getResponse();
		assertThat(response.getHeader(HttpHeaders.ETAG)).matches(WEAK_TAG);
		return response.getHeader(HttpHeaders.ETAG);
	}

	private static int statusOf(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
		return actions.andReturn().getResponse().getStatus();
	}
}
