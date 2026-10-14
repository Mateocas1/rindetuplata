package ar.rindetuplata.ingest;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared state of the LIVE port stubs: how often each port was asked (so a restart can prove which steps were
 * skipped) and which port misbehaves in a test.
 */
final class StubSources {

	static final AtomicInteger PLAZO_FIJO_CALLS = new AtomicInteger();
	static final AtomicInteger FUND_CALLS = new AtomicInteger();
	static final AtomicInteger CRYPTO_CALLS = new AtomicInteger();
	static final AtomicInteger MACRO_CALLS = new AtomicInteger();
	static final AtomicInteger DOLLAR_CALLS = new AtomicInteger();
	static final AtomicInteger UALA_CALLS = new AtomicInteger();
	static final AtomicInteger CURATED_CALLS = new AtomicInteger();

	/** When true the crypto port throws a {@code SourceException}: its source fails, the job still completes. */
	static boolean CRYPTO_SOURCE_FAILURE;

	/** When true the first crypto call throws an unexpected error, failing its step (and the job) once. */
	static boolean CRYPTO_FAILS_ONCE;

	private StubSources() {
	}

	static void reset() {
		PLAZO_FIJO_CALLS.set(0);
		FUND_CALLS.set(0);
		CRYPTO_CALLS.set(0);
		MACRO_CALLS.set(0);
		DOLLAR_CALLS.set(0);
		UALA_CALLS.set(0);
		CURATED_CALLS.set(0);
		CRYPTO_SOURCE_FAILURE = false;
		CRYPTO_FAILS_ONCE = false;
	}
}
