package ar.rindetuplata.api;

import ar.rindetuplata.source.fund.ArgentinaDatosFundAdapter;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Helpers of the cache and conditional-request tests. {@link #behindTheVersion} changes stored data without any
 * writer's footprint (no {@code source_run} row, no curation audit), which is what a stale cache would never notice;
 * {@link #poisonedFundClass} and {@link #poisonedRate} store rows the ranking cannot read (an unknown confidence),
 * so any path that reads them throws, which makes "this request did not rank" and "this read stayed targeted"
 * observable over HTTP.
 */
abstract class CachedRankingSupport extends RankingApiIntegrationTest {

	static final LocalDate CLOCK_DAY = ApiTestConfiguration.TODAY;

	@Autowired
	org.springframework.jdbc.core.simple.JdbcClient rawJdbc;

	private static final AtomicInteger NEXT_CLIENT = new AtomicInteger();

	/** A client address of this test's own, so a test that sends many requests never drains another one's bucket. */
	final String clientAddress = nextClientAddress();

	private static String nextClientAddress() {
		int n = NEXT_CLIENT.incrementAndGet();
		return "10.77." + (n / 250) + "." + (n % 250 + 1);
	}

	RequestPostProcessor fromThisClient() {
		return request -> {
			request.setRemoteAddr(clientAddress);
			return request;
		};
	}

	/** Changes the stored non-client rate of a bank without touching anything the data version is made of. */
	void behindTheVersion(long bankId, String rate) {
		rawJdbc.sql("update rate_snapshot set rate = :rate where instrument_id = :id")
				.param("rate", new java.math.BigDecimal(rate))
				.param("id", bankId)
				.update();
	}

	/** Makes every stored rate row unreadable in place: no new row, so the data version does not move. */
	void poisonStoredRates() {
		rawJdbc.sql("update rate_snapshot set confidence = 'NOT_A_CONFIDENCE'").update();
	}

	void poisonedFundClass(String name) {
		long id = poisonedInstrument("FUND_CLASS", ArgentinaDatosFundAdapter.SOURCE.id(), name);
		rawJdbc.sql("""
				insert into fund_quote (instrument_id, quote_date, vcp, fetched_at, confidence, flags)
				values (:id, :day, 1, now(), 'NOT_A_CONFIDENCE', '[]'::jsonb)
				""").param("id", id).param("day", CAPTURE).update();
	}

	void poisonedRate(String kind, String sourceId, String name) {
		long id = poisonedInstrument(kind, sourceId, name);
		rawJdbc.sql("""
				insert into rate_snapshot (instrument_id, capture_date, basis, rate, details, confidence, fetched_at, flags)
				values (:id, :day, 'TNA', 0.2, '{}'::jsonb, 'NOT_A_CONFIDENCE', now(), '[]'::jsonb)
				""").param("id", id).param("day", CAPTURE).update();
	}

	private long poisonedInstrument(String kind, String sourceId, String name) {
		return rawJdbc.sql("""
				insert into instrument (kind, source_id, natural_key, display_name, first_seen, last_seen)
				values (:kind, :source, :name, :name, :day, :day) returning id
				""").param("kind", kind).param("source", sourceId).param("name", name).param("day", CAPTURE)
				.query(Long.class).single();
	}
}
