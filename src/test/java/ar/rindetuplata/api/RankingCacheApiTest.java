package ar.rindetuplata.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.rindetuplata.curation.CuratedWalletRateService;
import ar.rindetuplata.curation.TierRequest;
import ar.rindetuplata.curation.WalletRateRequest;
import ar.rindetuplata.domain.Money;
import ar.rindetuplata.ranking.PublicRanking;
import ar.rindetuplata.ranking.RankedKind;
import ar.rindetuplata.ranking.RankingQuery;
import ar.rindetuplata.ranking.RankingResult;
import ar.rindetuplata.ranking.RankingService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.ResultActions;

/**
 * T3a rules (S3, L22): the public ranking is cached per data version and resolved query. A cached answer is only
 * ever served while the data is the same; a new ingestion run or a curation write must show up on the next request,
 * because a stale cache would give wrong numbers silently. The cache is bounded, and {@code rankAll} (alerts) never
 * uses it. "Behind the version" changes stored data with no writer footprint, which is what exposes a cache hit.
 */
class RankingCacheApiTest extends CachedRankingSupport {

	@Autowired
	RankingService rankingService;

	@Autowired
	CuratedWalletRateService curation;

	@Autowired
	PublicRanking publicRanking;

	@Value("${rindetuplata.ranking.cache-max-entries}")
	int cap;

	@Test
	void aRepeatedRequestIsServedFromTheCacheWhileTheDataVersionIsTheSame() throws Exception {
		long bank = seedBank("Banco A", "0.30", null);

		ranking("kind", "PLAZO_FIJO").andExpect(jsonPath("$.ranked[0].rateUsed.fraction").value(0.30));
		behindTheVersion(bank, "0.10");

		ranking("kind", "PLAZO_FIJO").andExpect(jsonPath("$.ranked[0].rateUsed.fraction").value(0.30));
	}

	@Test
	void aDifferentQueryIsNotServedFromAnotherQuerysEntry() throws Exception {
		long bank = seedBank("Banco A", "0.30", null);
		ranking("kind", "PLAZO_FIJO");
		behindTheVersion(bank, "0.10");

		ranking("kind", "PLAZO_FIJO", "days", "60").andExpect(jsonPath("$.ranked[0].rateUsed.fraction").value(0.10));
	}

	@Test
	void aNewIngestionRunInvalidatesTheCache() throws Exception {
		long bank = seedBank("Banco A", "0.30", null);
		ranking("kind", "PLAZO_FIJO").andExpect(jsonPath("$.ranked[0].rateUsed.fraction").value(0.30));
		behindTheVersion(bank, "0.10");

		recordIngestionRun();

		ranking("kind", "PLAZO_FIJO").andExpect(jsonPath("$.ranked[0].rateUsed.fraction").value(0.10));
	}

	@Test
	void aCurationWriteInvalidatesTheCache() throws Exception {
		ranking("kind", "WALLET").andExpect(jsonPath("$.ranked[*].name").value(not(hasItem("Naranja X"))));

		curation.save("naranja-x", new WalletRateRequest("Naranja X",
				List.of(new TierRequest(null, new BigDecimal("1000000"), new BigDecimal("20"))), List.of(), null),
				"admin@rindetuplata.ar");

		ranking("kind", "WALLET")
				.andExpect(jsonPath("$.ranked[?(@.name=='Naranja X')].rateUsed.fraction").value(contains(0.20)));
	}

	@Test
	void aCurationConfirmationInvalidatesTheCacheToo() throws Exception {
		long bank = seedBank("Banco A", "0.30", null);
		curation.save("naranja-x", new WalletRateRequest("Naranja X",
				List.of(new TierRequest(null, new BigDecimal("1000000"), new BigDecimal("20"))), List.of(), null),
				"admin@rindetuplata.ar");
		ranking("kind", "PLAZO_FIJO").andExpect(jsonPath("$.ranked[0].rateUsed.fraction").value(0.30));
		behindTheVersion(bank, "0.10");

		curation.confirm("naranja-x", "admin@rindetuplata.ar");

		ranking("kind", "PLAZO_FIJO").andExpect(jsonPath("$.ranked[0].rateUsed.fraction").value(0.10));
	}

	@Test
	void theCacheHoldsAtMostItsCapAndEvictsTheLeastRecentlyUsedQuery() throws Exception {
		long bank = seedBank("Banco A", "0.30", null);
		// cap + 1 distinct queries: the first one is the least recently used when the last one is stored.
		for (int i = 0; i <= cap; i++) {
			ranking("kind", "PLAZO_FIJO", "amount", String.valueOf(1000000 + i));
		}
		behindTheVersion(bank, "0.10");
		assertThat(publicRanking.cachedEntries()).as("never more than the cap").isEqualTo(cap);

		ranking("kind", "PLAZO_FIJO", "amount", String.valueOf(1000000 + cap))
				.andExpect(jsonPath("$.ranked[0].rateUsed.fraction").value(0.30));
		ranking("kind", "PLAZO_FIJO", "amount", "1000000")
				.andExpect(jsonPath("$.ranked[0].rateUsed.fraction").value(0.10));
		assertThat(publicRanking.cachedEntries()).isEqualTo(cap);
	}

	@Test
	void rankAllIsNeverCached() throws Exception {
		long bank = seedBank("Banco A", "0.30", null);
		RankingQuery query = new RankingQuery(Money.ars("1000000"), 30, Set.of(), Set.of(RankedKind.PLAZO_FIJO),
				ApiTestConfiguration.TODAY, 50);
		assertThat(rankingService.rankAll(query).ranked().get(0).rateUsed().fraction()).isEqualByComparingTo("0.30");
		behindTheVersion(bank, "0.10");

		RankingResult again = rankingService.rankAll(query);

		assertThat(again.ranked().get(0).rateUsed().fraction()).isEqualByComparingTo("0.10");
	}

	@Test
	void aCachedAnswerStaysIdenticalToTheFreshOne() throws Exception {
		seedAll();
		String first = body(ranking());
		String second = body(ranking());
		recordIngestionRun();
		String fresh = body(ranking());

		assertThat(second).isEqualTo(first);
		assertThat(fresh).isEqualTo(first);
	}

	private ResultActions ranking(String... params) throws Exception {
		var request = get("/api/v1/ranking").with(fromThisClient());
		for (int i = 0; i < params.length; i += 2) {
			request.param(params[i], params[i + 1]);
		}
		return mockMvc.perform(request).andExpect(status().isOk());
	}

	private static String body(ResultActions actions) throws Exception {
		return actions.andReturn().getResponse().getContentAsString();
	}
}
