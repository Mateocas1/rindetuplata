package ar.rindetuplata.api;

import ar.rindetuplata.store.BankPlazoFijoWriter;
import ar.rindetuplata.store.CryptoYieldWriter;
import ar.rindetuplata.store.FundQuoteWriter;
import ar.rindetuplata.store.MacroObservationWriter;
import ar.rindetuplata.store.OfficialPlazoFijoWriter;
import ar.rindetuplata.store.RunMode;
import ar.rindetuplata.store.RunStatus;
import ar.rindetuplata.store.SourceRunWriter;
import ar.rindetuplata.store.StoreReader;
import ar.rindetuplata.store.WalletRateWriter;
import ar.rindetuplata.source.fund.FundQuote;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared setup of the {@code api} tests: the whole application over a Testcontainers Postgres, a frozen clock, the
 * scheduler disabled, and one rolled-back transaction per test so the writers' rows never leak between tests.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *"
})
@AutoConfigureMockMvc
@Import(ApiTestConfiguration.class)
@Transactional
abstract class ApiIntegrationTest {

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected StoreReader reader;

	@Autowired
	protected OfficialPlazoFijoWriter officialWriter;

	@Autowired
	protected BankPlazoFijoWriter bankWriter;

	@Autowired
	protected FundQuoteWriter fundWriter;

	@Autowired
	protected CryptoYieldWriter cryptoWriter;

	@Autowired
	protected WalletRateWriter walletWriter;

	@Autowired
	protected MacroObservationWriter macroWriter;

	@Autowired
	protected SourceRunWriter sourceRunWriter;

	@Autowired
	protected JdbcClient jdbc;

	private static final AtomicLong RUNS = new AtomicLong();

	/**
	 * Records an ingestion run the way the daily job does after it writes: one {@code source_run} row, replaced on a
	 * re-run, with a later {@code recorded_at} every call. The public ranking is cached per data version, so a test
	 * that writes ranking data and then reads the API calls this to model the run that wrote it.
	 */
	protected void recordIngestionRun() {
		sourceRunWriter.record(ApiFixtures.CAPTURE, "api-test-source", RunMode.LIVE, RunStatus.OK, 1, null,
				Instant.parse("2026-10-14T19:30:00Z").plusSeconds(RUNS.incrementAndGet()));
	}

	/** Seeds every kind from the recorded 2026-10-14 fixtures through the T4 store writers. */
	protected void seedAll() {
		officialWriter.write(ApiFixtures.official(), ApiFixtures.CAPTURE);
		bankWriter.write(ApiFixtures.banks(), ApiFixtures.CAPTURE);
		fundWriter.write(ApiFixtures.fundQuotes("argentinadatos/fci-mercado-dinero-penultimo.json"),
				ApiFixtures.CAPTURE);
		fundWriter.write(ApiFixtures.fundQuotes("argentinadatos/fci-mercado-dinero-ultimo.json"),
				ApiFixtures.CAPTURE);
		// Each quote date arrives in its own source response, so they are written one by one.
		for (FundQuote quote : ApiFixtures.fallenFundQuotes()) {
			fundWriter.write(List.of(quote), ApiFixtures.CAPTURE);
		}
		cryptoWriter.write(ApiFixtures.crypto(), ApiFixtures.CAPTURE);
		walletWriter.write(List.of(ApiFixtures.uala(), ApiFixtures.curatedWallet()), ApiFixtures.CAPTURE);
		macroWriter.write(ApiFixtures.monthlyInflation(), ApiFixtures.CAPTURE);
		recordIngestionRun();
	}
}
