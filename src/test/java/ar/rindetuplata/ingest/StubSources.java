package ar.rindetuplata.ingest;

import ar.rindetuplata.source.macro.MacroVariable;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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

	/** The days the backfill asked the dated FCI port for, in order. */
	static final List<LocalDate> FUND_HISTORY_DAYS = new CopyOnWriteArrayList<>();

	/** Days for which the dated FCI port throws a {@code SourceException}. */
	static final Set<LocalDate> FUND_HISTORY_FAILING_DAYS = ConcurrentHashMap.newKeySet();

	/** Days for which the dated FCI port has no quotes (a weekend or a holiday: the live API answers 404). */
	static final Set<LocalDate> FUND_HISTORY_EMPTY_DAYS = ConcurrentHashMap.newKeySet();

	/** How often the MEP series port was read. */
	static final AtomicInteger DOLLAR_SERIES_CALLS = new AtomicInteger();

	/** When true the MEP series port throws a {@code SourceException}. */
	static boolean DOLLAR_SERIES_FAILS;

	/** One entry per macro range call: {@code VARIABLE|from|to}. */
	static final List<String> MACRO_RANGE_CALLS = new CopyOnWriteArrayList<>();

	/** Variables whose macro range call throws a {@code SourceException}. */
	static final Set<MacroVariable> MACRO_RANGE_FAILING = ConcurrentHashMap.newKeySet();

	/** The delays the backfill asked to wait, in order. */
	static final List<Duration> PAUSES = new CopyOnWriteArrayList<>();

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
		FUND_HISTORY_DAYS.clear();
		FUND_HISTORY_FAILING_DAYS.clear();
		FUND_HISTORY_EMPTY_DAYS.clear();
		DOLLAR_SERIES_CALLS.set(0);
		DOLLAR_SERIES_FAILS = false;
		MACRO_RANGE_CALLS.clear();
		MACRO_RANGE_FAILING.clear();
		PAUSES.clear();
	}
}
