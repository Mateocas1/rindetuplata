package ar.rindetuplata.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * T3a rules (S3, L22) for the bounded in-process cache behind the public ranking: it never holds more than its
 * cap, evicts the least recently used entry first (a read counts as a use), replaces an existing key in place, and
 * stays consistent under concurrent use.
 */
class LruCacheTest {

	@Test
	void aMissIsEmptyAndAHitReturnsTheStoredValue() {
		LruCache<String, Integer> cache = new LruCache<>(2);

		assertThat(cache.get("a")).isEmpty();
		cache.put("a", 1);

		assertThat(cache.get("a")).contains(1);
		assertThat(cache.size()).isEqualTo(1);
	}

	@Test
	void theLeastRecentlyUsedEntryIsEvictedWhenTheCapIsExceeded() {
		LruCache<String, Integer> cache = new LruCache<>(2);
		cache.put("a", 1);
		cache.put("b", 2);

		cache.put("c", 3);

		assertThat(cache.get("a")).isEmpty();
		assertThat(cache.get("b")).contains(2);
		assertThat(cache.get("c")).contains(3);
		assertThat(cache.size()).isEqualTo(2);
	}

	@Test
	void readingAnEntryProtectsItFromEviction() {
		LruCache<String, Integer> cache = new LruCache<>(2);
		cache.put("a", 1);
		cache.put("b", 2);
		cache.get("a");

		cache.put("c", 3);

		assertThat(cache.get("a")).contains(1);
		assertThat(cache.get("b")).isEmpty();
	}

	@Test
	void puttingAnExistingKeyReplacesItsValueWithoutGrowing() {
		LruCache<String, Integer> cache = new LruCache<>(2);
		cache.put("a", 1);
		cache.put("b", 2);

		cache.put("a", 10);

		assertThat(cache.get("a")).contains(10);
		assertThat(cache.get("b")).contains(2);
		assertThat(cache.size()).isEqualTo(2);
	}

	@Test
	void aCapBelowOneIsRejected() {
		assertThatThrownBy(() -> new LruCache<String, Integer>(0)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("0");
	}

	@Test
	void concurrentUseNeverExceedsTheCapAndLosesNoReadableValue() throws Exception {
		int cap = 16;
		LruCache<Integer, Integer> cache = new LruCache<>(cap);
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Callable<Boolean>> workers = new ArrayList<>();
			for (int worker = 0; worker < 8; worker++) {
				int offset = worker;
				workers.add(() -> {
					for (int i = 0; i < 5_000; i++) {
						int key = (i * 7 + offset) % 100;
						cache.put(key, key * 2);
						cache.get((key + 1) % 100).ifPresent(value -> assertThat(value % 2).isZero());
						assertThat(cache.size()).isLessThanOrEqualTo(cap);
					}
					return true;
				});
			}
			for (Future<Boolean> done : pool.invokeAll(workers, 60, TimeUnit.SECONDS)) {
				assertThat(done.get()).isTrue();
			}
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(cache.size()).isEqualTo(cap);
	}
}
