package ar.rindetuplata.store;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Segment;
import ar.rindetuplata.source.dollar.ArgentinaDatosDollarAdapter;
import ar.rindetuplata.source.dollar.DatedDollarQuote;
import ar.rindetuplata.source.dollar.DolarApiAdapter;
import ar.rindetuplata.source.dollar.DollarQuote;
import ar.rindetuplata.source.fund.ArgentinaDatosFundAdapter;
import ar.rindetuplata.source.fund.FundQuote;
import ar.rindetuplata.source.macro.BcraMonetariasClient;
import ar.rindetuplata.source.macro.MacroObservation;
import ar.rindetuplata.source.macro.MacroVariable;
import ar.rindetuplata.store.StoreReader.DollarRow;
import ar.rindetuplata.store.StoreReader.FundQuoteRow;
import ar.rindetuplata.store.StoreReader.InstrumentRow;
import ar.rindetuplata.store.StoreReader.MacroRow;
import ar.rindetuplata.store.StoreReader.SourceRunRow;
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
 * T4a (L33 a): backfill writes are insert-only, so a row that is already stored (live data) is never overwritten, and
 * the one {@code source_run} row per source and day that a backfill records replaces only an earlier BACKFILL row,
 * never a LIVE or REPLAY one.
 */
@Import(StoreTestConfiguration.class)
@SpringBootTest
@Transactional
class StoreBackfillWriterTest {

	private static final Instant LIVE_AT = Instant.parse("2026-10-14T19:06:23Z");
	private static final Instant BACKFILL_AT = Instant.parse("2026-11-01T12:00:00Z");
	private static final LocalDate DAY = LocalDate.parse("2026-10-01");
	private static final LocalDate OLDER_DAY = LocalDate.parse("2026-09-30");
	private static final String FUND = "Test Fondo - Clase A";

	@Autowired
	FundQuoteWriter funds;

	@Autowired
	DollarQuoteWriter dollars;

	@Autowired
	MacroObservationWriter macros;

	@Autowired
	SourceRunWriter runs;

	@Autowired
	StoreReader reader;

	private static FundQuote fund(LocalDate date, String vcp, Instant fetchedAt) {
		return new FundQuote(ArgentinaDatosFundAdapter.SOURCE, FUND, "Test Fondo", "Clase A", "corto",
				Segment.SAVER, date, new BigDecimal(vcp), new BigDecimal("100"), new BigDecimal("200"), fetchedAt,
				Confidence.AGGREGATOR_AUTOMATIC, List.of());
	}

	private static DatedDollarQuote mep(SourceRefHolder source, LocalDate date, String buy, String sell,
			Instant fetchedAt) {
		return new DatedDollarQuote(date, new DollarQuote(source.ref(), new BigDecimal(buy), new BigDecimal(sell),
				null, fetchedAt, Confidence.AGGREGATOR_AUTOMATIC, List.of()));
	}

	/** Names the source of a test quote without exposing the adapters' constants twice. */
	private enum SourceRefHolder {
		ARGENTINADATOS, DOLARAPI;

		ar.rindetuplata.source.SourceRef ref() {
			return this == ARGENTINADATOS ? ArgentinaDatosDollarAdapter.SOURCE : DolarApiAdapter.SOURCE;
		}
	}

	@Test
	void aFundQuoteThatIsStoredIsNeverOverwrittenAndOnlyTheMissingDaysAreInserted() {
		funds.write(List.of(fund(DAY, "111.5", LIVE_AT)), DAY);

		WriteResult stored = funds.writeMissing(List.of(fund(DAY, "999", BACKFILL_AT)), DAY);
		WriteResult missing = funds.writeMissing(List.of(fund(OLDER_DAY, "110.25", BACKFILL_AT)), OLDER_DAY);

		assertThat(stored.rows()).as("a stored quote counts as not written").isZero();
		assertThat(missing.rows()).as("a missing day counts as written").isEqualTo(1);
		FundQuoteRow live = reader.fundQuote(ArgentinaDatosFundAdapter.SOURCE.id(), FUND, DAY).orElseThrow();
		assertThat(live.vcp()).isEqualByComparingTo("111.5");
		assertThat(live.fetchedAt()).isEqualTo(LIVE_AT);
		FundQuoteRow older = reader.fundQuote(ArgentinaDatosFundAdapter.SOURCE.id(), FUND, OLDER_DAY).orElseThrow();
		assertThat(older.vcp()).isEqualByComparingTo("110.25");
		assertThat(older.fetchedAt()).isEqualTo(BACKFILL_AT);
		assertThat(reader.fundQuoteCount()).isEqualTo(2);
	}

	@Test
	void repeatingAFundBackfillWritesNothingTheSecondTime() {
		List<FundQuote> quotes = List.of(fund(OLDER_DAY, "110.25", BACKFILL_AT));
		assertThat(funds.writeMissing(quotes, OLDER_DAY).rows()).isEqualTo(1);

		assertThat(funds.writeMissing(quotes, OLDER_DAY).rows()).isZero();
		assertThat(reader.fundQuoteCount()).isEqualTo(1);
	}

	@Test
	void aBackfilledFundWidensTheInstrumentWindowBackwardsAndNeverShrinksIt() {
		funds.write(List.of(fund(DAY, "111.5", LIVE_AT)), DAY);

		funds.writeMissing(List.of(fund(OLDER_DAY, "110.25", BACKFILL_AT)), OLDER_DAY);

		InstrumentRow instrument = reader.instrument(InstrumentKind.FUND_CLASS, ArgentinaDatosFundAdapter.SOURCE.id(), FUND)
				.orElseThrow();
		assertThat(instrument.firstSeen()).isEqualTo(OLDER_DAY);
		assertThat(instrument.lastSeen()).isEqualTo(DAY);
	}

	@Test
	void aDollarQuoteThatIsStoredIsNeverOverwrittenAndTheTwoSourcesKeepTheirOwnRows() {
		dollars.write(List.of(mep(SourceRefHolder.DOLARAPI, DAY, "1534.2", "1539.4", LIVE_AT).quote()), DAY);

		WriteResult same = dollars.writeMissing(List.of(
				mep(SourceRefHolder.DOLARAPI, DAY, "1.0", "2.0", BACKFILL_AT)));
		WriteResult other = dollars.writeMissing(List.of(
				mep(SourceRefHolder.ARGENTINADATOS, DAY, "1537.2", "1548.7", BACKFILL_AT)));

		assertThat(same.rows()).isZero();
		assertThat(other.rows()).isEqualTo(1);
		DollarRow live = reader.dollar(DolarApiAdapter.SOURCE.id(), DAY).orElseThrow();
		assertThat(live.buy()).isEqualByComparingTo("1534.2");
		assertThat(live.sell()).isEqualByComparingTo("1539.4");
		assertThat(live.fetchedAt()).isEqualTo(LIVE_AT);
		DollarRow history = reader.dollar(ArgentinaDatosDollarAdapter.SOURCE.id(), DAY).orElseThrow();
		assertThat(history.buy()).isEqualByComparingTo("1537.2");
		assertThat(history.sell()).isEqualByComparingTo("1548.7");
		assertThat(history.fetchedAt()).isEqualTo(BACKFILL_AT);
		assertThat(reader.dollarCount()).isEqualTo(2);
	}

	@Test
	void repeatingADollarBackfillWritesNothingTheSecondTime() {
		List<DatedDollarQuote> series = List.of(mep(SourceRefHolder.ARGENTINADATOS, DAY, "1537.2", "1548.7", BACKFILL_AT));
		assertThat(dollars.writeMissing(series).rows()).isEqualTo(1);

		assertThat(dollars.writeMissing(series).rows()).isZero();
		assertThat(reader.dollarCount()).isEqualTo(1);
	}

	@Test
	void aMacroObservationThatIsStoredIsNeverOverwritten() {
		MacroObservation live = new MacroObservation(BcraMonetariasClient.SOURCE, MacroVariable.MONTHLY_INFLATION,
				LocalDate.parse("2026-09-30"), new BigDecimal("0.019"), LIVE_AT, Confidence.OFFICIAL_AUTOMATIC,
				List.of());
		macros.write(List.of(live), DAY);
		MacroObservation changed = new MacroObservation(BcraMonetariasClient.SOURCE, MacroVariable.MONTHLY_INFLATION,
				LocalDate.parse("2026-09-30"), new BigDecimal("0.5"), BACKFILL_AT, Confidence.OFFICIAL_AUTOMATIC,
				List.of());
		MacroObservation missing = new MacroObservation(BcraMonetariasClient.SOURCE, MacroVariable.MONTHLY_INFLATION,
				LocalDate.parse("2026-08-31"), new BigDecimal("0.018"), BACKFILL_AT, Confidence.OFFICIAL_AUTOMATIC,
				List.of());

		WriteResult result = macros.writeMissing(List.of(changed, missing), DAY);

		assertThat(result.rows()).isEqualTo(1);
		MacroRow kept = reader.macro(MacroVariable.MONTHLY_INFLATION, LocalDate.parse("2026-09-30"),
				BcraMonetariasClient.SOURCE.id()).orElseThrow();
		assertThat(kept.value()).isEqualByComparingTo("0.019");
		assertThat(kept.fetchedAt()).isEqualTo(LIVE_AT);
		assertThat(reader.macro(MacroVariable.MONTHLY_INFLATION, LocalDate.parse("2026-08-31"),
				BcraMonetariasClient.SOURCE.id())).isPresent();
		assertThat(reader.macroCount()).isEqualTo(2);
	}

	@Test
	void aBackfillRunIsRecordedWithTheBackfillModeAndReplacesOnlyAnEarlierBackfillRun() {
		runs.recordBackfill(DAY, "source-a", RunStatus.FAILED, 0, "boom", LIVE_AT);

		runs.recordBackfill(DAY, "source-a", RunStatus.OK, 7, null, BACKFILL_AT);

		SourceRunRow row = reader.sourceRun(DAY, "source-a").orElseThrow();
		assertThat(row.mode()).isEqualTo("BACKFILL");
		assertThat(row.status()).isEqualTo("OK");
		assertThat(row.rows()).isEqualTo(7);
		assertThat(row.failure()).isNull();
		assertThat(row.recordedAt()).isEqualTo(BACKFILL_AT);
		assertThat(reader.sourceRunCount(DAY)).isEqualTo(1);
	}

	@Test
	void aLiveOrReplayRunIsNeverReplacedByABackfillRun() {
		runs.record(DAY, "source-live", RunMode.LIVE, RunStatus.OK, 3, null, LIVE_AT);
		runs.record(DAY, "source-replay", RunMode.REPLAY, RunStatus.MISSING_IN_ARCHIVE, 0, "gone", LIVE_AT);

		runs.recordBackfill(DAY, "source-live", RunStatus.FAILED, 0, "boom", BACKFILL_AT);
		runs.recordBackfill(DAY, "source-replay", RunStatus.OK, 9, null, BACKFILL_AT);

		SourceRunRow live = reader.sourceRun(DAY, "source-live").orElseThrow();
		assertThat(live.mode()).isEqualTo("LIVE");
		assertThat(live.status()).isEqualTo("OK");
		assertThat(live.rows()).isEqualTo(3);
		assertThat(live.recordedAt()).isEqualTo(LIVE_AT);
		SourceRunRow replay = reader.sourceRun(DAY, "source-replay").orElseThrow();
		assertThat(replay.mode()).isEqualTo("REPLAY");
		assertThat(replay.status()).isEqualTo("MISSING_IN_ARCHIVE");
		assertThat(replay.failure()).isEqualTo("gone");
	}
}
