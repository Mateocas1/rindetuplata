package ar.rindetuplata.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.curation.CuratedWalletRateService;
import ar.rindetuplata.curation.TierRequest;
import ar.rindetuplata.curation.WalletRateRequest;
import ar.rindetuplata.store.BankPlazoFijoWriter;
import ar.rindetuplata.store.RunMode;
import ar.rindetuplata.store.RunStatus;
import ar.rindetuplata.store.SourceRunWriter;
import ar.rindetuplata.store.StoreTestConfiguration;
import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.plazofijo.ArgentinaDatosPlazoFijoAdapter;
import ar.rindetuplata.source.plazofijo.BankPlazoFijoRate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * T3a rules (S3, L22): the data version is read from the database and moves whenever a writer of ranking data
 * does: a first or a replaced {@code source_run} row (the daily job, the startup ingestion, a backfill all record
 * one), a curated wallet rate saved or confirmed, or an instrument seen for the first time. It stays put when
 * nothing was written, so a cache keyed by it is reused until the data changes.
 */
@Import(StoreTestConfiguration.class)
@SpringBootTest
@Transactional
class DataVersionTest {

	private static final LocalDate DAY = LocalDate.parse("2026-10-14");

	@Autowired
	DataVersion version;

	@Autowired
	SourceRunWriter runs;

	@Autowired
	CuratedWalletRateService curation;

	@Autowired
	BankPlazoFijoWriter banks;

	@Test
	void theVersionIsStableWhileNothingIsWritten() {
		assertThat(version.current()).isNotBlank().isEqualTo(version.current());
	}

	@Test
	void aNewSourceRunChangesTheVersion() {
		String before = version.current();

		runs.record(DAY, "source-a", RunMode.LIVE, RunStatus.OK, 3, null, Instant.parse("2026-10-14T19:30:00Z"));

		assertThat(version.current()).isNotEqualTo(before);
	}

	@Test
	void replacingASourceRunOfTheSameDayChangesTheVersionToo() {
		runs.record(DAY, "source-a", RunMode.LIVE, RunStatus.OK, 3, null, Instant.parse("2026-10-14T19:30:00Z"));
		String before = version.current();

		runs.record(DAY, "source-a", RunMode.LIVE, RunStatus.OK, 3, null, Instant.parse("2026-10-14T19:31:00Z"));

		assertThat(version.current()).isNotEqualTo(before);
	}

	@Test
	void savingAndConfirmingACuratedWalletRateChangeTheVersion() {
		String before = version.current();
		curation.save("test-wallet", new WalletRateRequest("Test Wallet",
				List.of(new TierRequest(null, new BigDecimal("1000000"), new BigDecimal("20"))), List.of(), null),
				"admin@rindetuplata.ar");
		String saved = version.current();

		curation.confirm("test-wallet", "admin@rindetuplata.ar");
		String confirmed = version.current();

		assertThat(List.of(before, saved, confirmed)).doesNotHaveDuplicates();
	}

	@Test
	void anInstrumentSeenForTheFirstTimeChangesTheVersion() {
		String before = version.current();

		banks.write(List.of(new BankPlazoFijoRate(ArgentinaDatosPlazoFijoAdapter.SOURCE, "Banco Nuevo", null,
				new BigDecimal("0.30"), List.of(), DAY, Instant.parse("2026-10-14T19:06:23Z"),
				Confidence.AGGREGATOR_AUTOMATIC, List.of())), DAY);

		assertThat(version.current()).isNotEqualTo(before);
	}
}
