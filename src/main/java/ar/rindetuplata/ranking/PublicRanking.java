package ar.rindetuplata.ranking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The cached front of {@link RankingService#rank} for the public API. A result is reused only for the same
 * {@linkplain DataVersion data version} and the same resolved query ({@code asOf} included, so the day rollover is a
 * new key), in a bounded in-process {@link LruCache}. The caller reads the version once per request and passes it to
 * {@link #rank}, so the ETag it sends and the entry it reads are the same version. Concurrent misses of one key
 * compute independently and the last write wins: the results are equal, and no lock is held while ranking.
 * {@link RankingService#rankAll} (the alerts) never comes through here.
 */
@Service
public class PublicRanking {

	private final RankingService ranking;

	private final DataVersion dataVersion;

	private final LruCache<CacheKey, RankingResult> cache;

	public PublicRanking(RankingService ranking, DataVersion dataVersion,
			@Value("${rindetuplata.ranking.cache-max-entries}") int maxEntries) {
		this.ranking = ranking;
		this.dataVersion = dataVersion;
		this.cache = new LruCache<>(maxEntries);
	}

	/** The current data version, one aggregate query. */
	public String dataVersion() {
		return dataVersion.current();
	}

	/** The ranking for {@code query} as of {@code version}: from the cache, or computed and stored. */
	public RankingResult rank(String version, RankingQuery query) {
		CacheKey key = new CacheKey(version, query.cacheKey());
		return cache.get(key).orElseGet(() -> {
			RankingResult computed = ranking.rank(query);
			cache.put(key, computed);
			return computed;
		});
	}

	/** How many results the cache holds right now, never more than its cap. */
	public int cachedEntries() {
		return cache.size();
	}

	private record CacheKey(String dataVersion, String query) {
	}
}
