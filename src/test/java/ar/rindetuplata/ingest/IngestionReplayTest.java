package ar.rindetuplata.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.source.crypto.ArgentinaDatosCryptoAdapter;
import ar.rindetuplata.source.plazofijo.ArgentinaDatosPlazoFijoAdapter;
import ar.rindetuplata.source.plazofijo.BcraPlazoFijoAdapter;
import ar.rindetuplata.source.wallet.UalaWalletAdapter;
import ar.rindetuplata.store.BankPlazoFijoWriter;
import ar.rindetuplata.store.CryptoYieldWriter;
import ar.rindetuplata.store.InstrumentKind;
import ar.rindetuplata.store.OfficialPlazoFijoWriter;
import ar.rindetuplata.store.RunMode;
import ar.rindetuplata.store.StoreReader;
import ar.rindetuplata.store.StoreReader.SourceRunRow;
import ar.rindetuplata.store.StoreTestConfiguration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * T4c replay rules: the real 2026-10-14 archive writes the archived kinds with the manifest instant and REPLAY
 * source runs; a source that is not ok or absent is {@code MISSING_IN_ARCHIVE} while the others are written; a
 * missing manifest fails the job naming the path. The same context has
 * {@code rindetuplata.ingest.enabled=false}, which must leave the scheduler unregistered.
 */
@Import(StoreTestConfiguration.class)
@SpringBootTest
class IngestionReplayTest {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("rindetuplata.ingest.enabled", () -> "false");
		registry.add("rindetuplata.ingest.cron", () -> "0 0 0 1 1 *");
		registry.add("rindetuplata.ingest.archive-dir", () -> TestArchives.ROOT.toString());
	}

	static final String OFFICIAL = BcraPlazoFijoAdapter.SOURCE.id();
	static final String BANKS = ArgentinaDatosPlazoFijoAdapter.SOURCE.id();
	static final String CRYPTO = ArgentinaDatosCryptoAdapter.SOURCE.id();
	static final String UALA = UalaWalletAdapter.SOURCE.id();

	@Autowired
	IngestionService ingestionService;

	@Autowired
	StoreReader reader;

	@Autowired
	ApplicationContext context;

	@Test
	void replayOfTheRealArchiveWritesTheArchivedKindsWithTheManifestInstant() {
		IngestionResult result = ingestionService.run(TestArchives.REAL_DAY, RunMode.REPLAY);

		assertThat(result.status()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(result.alreadyCompleted()).isFalse();
		assertThat(reader.sourceRunCount(TestArchives.REAL_DAY)).isEqualTo(4);

		var official = BcraPlazoFijoAdapter.parse(TestArchives.body(TestArchives.REAL_DAY, OFFICIAL),
				TestArchives.REAL_CAPTURED_AT);
		var banks = ArgentinaDatosPlazoFijoAdapter.parse(TestArchives.body(TestArchives.REAL_DAY, BANKS),
				TestArchives.REAL_CAPTURED_AT);
		var crypto = ArgentinaDatosCryptoAdapter.parse(TestArchives.body(TestArchives.REAL_DAY, CRYPTO),
				TestArchives.REAL_CAPTURED_AT);

		var officialRow = reader.snapshot(InstrumentKind.PLAZO_FIJO_OFFICIAL, OFFICIAL,
				OfficialPlazoFijoWriter.naturalKey(official.get(0)), TestArchives.REAL_DAY).orElseThrow();
		assertThat(officialRow.fetchedAt()).isEqualTo(TestArchives.REAL_CAPTURED_AT);
		var bankRow = reader.snapshot(InstrumentKind.PLAZO_FIJO_BANK, BANKS,
				BankPlazoFijoWriter.naturalKey(banks.get(0)), TestArchives.REAL_DAY).orElseThrow();
		assertThat(bankRow.fetchedAt()).isEqualTo(TestArchives.REAL_CAPTURED_AT);
		var cryptoRow = reader.snapshot(InstrumentKind.CRYPTO_YIELD, CRYPTO,
				CryptoYieldWriter.naturalKey(crypto.get(0)), TestArchives.REAL_DAY).orElseThrow();
		assertThat(cryptoRow.fetchedAt()).isEqualTo(TestArchives.REAL_CAPTURED_AT);
		var walletRow = reader.snapshot(InstrumentKind.WALLET, UALA, "uala", TestArchives.REAL_DAY).orElseThrow();
		assertThat(walletRow.fetchedAt()).isEqualTo(TestArchives.REAL_CAPTURED_AT);

		assertReplayRow(TestArchives.REAL_DAY, OFFICIAL);
		assertReplayRow(TestArchives.REAL_DAY, BANKS);
		assertReplayRow(TestArchives.REAL_DAY, CRYPTO);
		assertReplayRow(TestArchives.REAL_DAY, UALA);
		assertThat(runRow(TestArchives.REAL_DAY, OFFICIAL).rows())
			.isEqualTo((int) official.stream().map(OfficialPlazoFijoWriter::naturalKey).distinct().count());
		assertThat(runRow(TestArchives.REAL_DAY, BANKS).rows())
			.isEqualTo((int) banks.stream().map(BankPlazoFijoWriter::naturalKey).distinct().count());
		assertThat(runRow(TestArchives.REAL_DAY, CRYPTO).rows())
			.isEqualTo((int) crypto.stream().map(CryptoYieldWriter::naturalKey).distinct().count());
		assertThat(runRow(TestArchives.REAL_DAY, UALA).rows()).isEqualTo(1);

		assertThat(reader.fundQuoteCount()).isZero();
		assertThat(reader.macroCount()).isZero();
		assertThat(reader.dollarCount()).isZero();
	}

	@Test
	void aSourceNotOkOrAbsentIsMissingInArchiveWhileTheOthersAreWritten() {
		IngestionResult result = ingestionService.run(TestArchives.PARTIAL_DAY, RunMode.REPLAY);

		assertThat(result.status()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(reader.sourceRunCount(TestArchives.PARTIAL_DAY)).isEqualTo(4);
		assertThat(runRow(TestArchives.PARTIAL_DAY, OFFICIAL).status()).isEqualTo("OK");
		assertThat(runRow(TestArchives.PARTIAL_DAY, CRYPTO).status()).isEqualTo("OK");

		SourceRunRow notOk = runRow(TestArchives.PARTIAL_DAY, BANKS);
		assertThat(notOk.status()).isEqualTo("MISSING_IN_ARCHIVE");
		assertThat(notOk.mode()).isEqualTo("REPLAY");
		assertThat(notOk.rows()).isZero();
		SourceRunRow absent = runRow(TestArchives.PARTIAL_DAY, UALA);
		assertThat(absent.status()).isEqualTo("MISSING_IN_ARCHIVE");
		assertThat(absent.rows()).isZero();

		var official = BcraPlazoFijoAdapter.parse(TestArchives.body(TestArchives.PARTIAL_DAY, OFFICIAL),
				TestArchives.PARTIAL_CAPTURED_AT);
		var crypto = ArgentinaDatosCryptoAdapter.parse(TestArchives.body(TestArchives.PARTIAL_DAY, CRYPTO),
				TestArchives.PARTIAL_CAPTURED_AT);
		assertThat(reader.snapshot(InstrumentKind.PLAZO_FIJO_OFFICIAL, OFFICIAL,
				OfficialPlazoFijoWriter.naturalKey(official.get(0)), TestArchives.PARTIAL_DAY)).isPresent();
		assertThat(reader.snapshot(InstrumentKind.CRYPTO_YIELD, CRYPTO,
				CryptoYieldWriter.naturalKey(crypto.get(0)), TestArchives.PARTIAL_DAY)).isPresent();

		var banks = ArgentinaDatosPlazoFijoAdapter.parse(TestArchives.body(TestArchives.PARTIAL_DAY, BANKS),
				TestArchives.PARTIAL_CAPTURED_AT);
		assertThat(reader.snapshot(InstrumentKind.PLAZO_FIJO_BANK, BANKS,
				BankPlazoFijoWriter.naturalKey(banks.get(0)), TestArchives.PARTIAL_DAY)).isEmpty();
		assertThat(reader.snapshot(InstrumentKind.WALLET, UALA, "uala", TestArchives.PARTIAL_DAY)).isEmpty();

		var written = reader.snapshot(InstrumentKind.PLAZO_FIJO_OFFICIAL, OFFICIAL,
				OfficialPlazoFijoWriter.naturalKey(official.get(0)), TestArchives.PARTIAL_DAY).orElseThrow();
		assertThat(written.fetchedAt()).isEqualTo(TestArchives.PARTIAL_CAPTURED_AT);
	}

	@Test
	void aMissingManifestFailsTheJobNamingThePath() {
		IngestionResult result = ingestionService.run(TestArchives.NO_MANIFEST_DAY, RunMode.REPLAY);

		assertThat(result.status()).isEqualTo(BatchStatus.FAILED);
		assertThat(result.alreadyCompleted()).isFalse();
		String missing = TestArchives.manifestPath(TestArchives.NO_MANIFEST_DAY).toString();
		assertThat(result.failureMessages()).anySatisfy(message -> assertThat(message).contains(missing));
		assertThat(reader.sourceRunCount(TestArchives.NO_MANIFEST_DAY)).isZero();
	}

	@Test
	void theSchedulerIsNotRegisteredWhenIngestionIsDisabled() {
		assertThat(context.getBeanNamesForType(IngestionScheduler.class)).isEmpty();
	}

	private void assertReplayRow(LocalDate date, String sourceId) {
		SourceRunRow row = runRow(date, sourceId);
		assertThat(row.mode()).as(sourceId).isEqualTo("REPLAY");
		assertThat(row.status()).as(sourceId).isEqualTo("OK");
	}

	private SourceRunRow runRow(LocalDate date, String sourceId) {
		return reader.sourceRun(date, sourceId)
			.orElseThrow(() -> new AssertionError("no source_run row for " + sourceId + " on " + date));
	}
}
