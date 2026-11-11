package ar.rindetuplata.report;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A small least-recently-used cache with a fixed entry cap, no dependency. It is the same few lines as the
 * ranking's cache: that one is package private to {@code ranking}, which this package does not touch. Every method takes
 * the same monitor, so a lookup (which reorders the access list), an insert and an eviction never interleave; the
 * critical sections are a few hash operations and the lock is never held across a computation.
 */
final class LruCache<K, V> {

	private final LinkedHashMap<K, V> entries;

	LruCache(int maxEntries) {
		if (maxEntries < 1) {
			throw new IllegalArgumentException("a cache holds at least 1 entry: " + maxEntries);
		}
		this.entries = new LinkedHashMap<>(16, 0.75f, true) {

			@Override
			protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
				return size() > maxEntries;
			}
		};
	}

	/** The cached value, which also marks the entry as the most recently used. */
	synchronized Optional<V> get(K key) {
		return Optional.ofNullable(entries.get(key));
	}

	/** Stores or replaces the value; past the cap the least recently used entry is dropped. */
	synchronized void put(K key, V value) {
		entries.put(key, value);
	}

	synchronized int size() {
		return entries.size();
	}
}
