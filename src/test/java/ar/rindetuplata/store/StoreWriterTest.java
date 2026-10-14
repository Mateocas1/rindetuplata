package ar.rindetuplata.store;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Fixtures;
import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.ValidationFlag;
import ar.rindetuplata.source.crypto.ArgentinaDatosCryptoAdapter;
import ar.rindetuplata.source.crypto.CryptoYield;
import ar.rindetuplata.source.dollar.DolarApiAdapter;
import ar.rindetuplata.source.dollar.DollarQuote;
import ar.rindetuplata.source.fund.ArgentinaDatosFundAdapter;
import ar.rindetuplata.source.fund.FundQuote;
import ar.rindetuplata.source.macro.ArgentinaDatosInflationAdapter;
import ar.rindetuplata.source.macro.MacroObservation;
import ar.rindetuplata.source.macro.MacroVariable;
import ar.rindetuplata.source.plazofijo.ArgentinaDatosPlazoFijoAdapter;
import ar.rindetuplata.source.plazofijo.BankPlazoFijoRate;
import ar.rindetuplata.source.plazofijo.BcraPlazoFijoAdapter;
import ar.rindetuplata.source.plazofijo.OfficialPlazoFijoRate;
import ar.rindetuplata.source.plazofijo.TermRate;
import ar.rindetuplata.source.wallet.CuratedWalletRateAdapter;
import ar.rindetuplata.source.wallet.UalaWalletAdapter;
import ar.rindetuplata.source.wallet.WalletRate;
import ar.rindetuplata.store.StoreReader.FundQuoteRow;
import ar.rindetuplata.store.StoreReader.InstrumentRow;
import ar.rindetuplata.store.StoreReader.SnapshotRow;
import ar.rindetuplata.store.StoreReader.SourceRunRow;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * T4a store rules, against a real Postgres: every writer stores what its record holds, re-running a date replaces
 * instead of duplicating, INVALID rows are kept, duplicates keep the first and are counted, and the instrument
 * window widens only. Records come from the recorded fixtures through the T3 parse methods.
 */
@Import(StoreTestConfiguration.class)
@SpringBootTest
@Transactional
class StoreWriterTest {

	/** The capture instant of the recorded 2026-10-14 archive, so the flags match production. */
	static final Instant FETCHED = Instant.parse("2026-10-14T19:06:23Z");
	static final LocalDate CAPTURE = LocalDate.parse("2026-10-14");

	@Autowired
	OfficialPlazoFijoWriter officialWriter;

	@Autowired
	BankPlazoFijoWriter bankWriter;

	@Autowired
	FundQuoteWriter fundWriter;

	@Autowired
	CryptoYieldWriter cryptoWriter;

	@Autowired
	MacroObservationWriter macroWriter;

	@Autowired
	DollarQuoteWriter dollarWriter;

	@Autowired
	WalletRateWriter walletWriter;

	@Autowired
	SourceRunWriter sourceRunWriter;

	@Autowired
	StoreReader reader;

	@Test
	void officialWriterStoresWhatTheRecordHolds() {
		List<OfficialPlazoFijoRate> records = officialRecords();
		WriteResult result = officialWriter.write(records, CAPTURE);

		long distinct = records.stream().map(OfficialPlazoFijoWriter::naturalKey).distinct().count();
		assertThat(result.rows()).isEqualTo((int) distinct);
		assertThat(reader.snapshotCount(InstrumentKind.PLAZO_FIJO_OFFICIAL)).isEqualTo((int) distinct);
		assertThat(reader.instrumentCount(InstrumentKind.PLAZO_FIJO_OFFICIAL)).isEqualTo((int) distinct);

		for (OfficialPlazoFijoRate record : records) {
			SnapshotRow row = snapshotOf(record);
			assertDecimalEquals(record.tea(), row.rate());
			assertThat(row.clientRate()).isNull();
			assertDecimalEquals(record.minimumAmount(), row.minimumAmount());
			assertThat(row.basis()).isEqualTo("TEA");
			assertThat(row.confidence()).isEqualTo(Confidence.OFFICIAL_AUTOMATIC.name());
			assertThat(row.dataDate()).isEqualTo(record.dataDate());
			assertThat(row.fetchedAt()).isEqualTo(record.fetchedAt());
			assertThat(row.verifiedAt()).isNull();
			assertThat(row.invalid()).isEqualTo(record.isInvalid());
			assertThat(row.flags()).containsExactlyElementsOf(record.flags());
			assertThat(jsonText(row.details().path("termCode"))).isEqualTo(text(record.termCode()));
			assertThat(jsonText(row.details().path("channel"))).isEqualTo(record.channel());
			assertThat(jsonText(row.details().path("productName"))).isEqualTo(record.productName());
		}

		InstrumentRow instrument = reader.instrument(InstrumentKind.PLAZO_FIJO_OFFICIAL, records.get(0).source().id(),
				OfficialPlazoFijoWriter.naturalKey(records.get(0))).orElseThrow();
		assertThat(instrument.displayName()).isEqualTo(records.get(0).productName());
		assertThat(instrument.entityName()).isEqualTo(records.get(0).entityName());
		assertThat(instrument.firstSeen()).isEqualTo(CAPTURE);
		assertThat(instrument.lastSeen()).isEqualTo(CAPTURE);
	}

	@Test
	void bankWriterStoresNonClientAsRateAndClientSeparately() {
		List<BankPlazoFijoRate> records = bankRecords();
		assertThat(records).isNotEmpty();
		WriteResult result = bankWriter.write(records, CAPTURE);

		assertThat(result.rows()).isEqualTo(records.size());
		assertThat(reader.snapshotCount(InstrumentKind.PLAZO_FIJO_BANK)).isEqualTo(records.size());

		for (BankPlazoFijoRate record : records) {
			SnapshotRow row = reader.snapshot(InstrumentKind.PLAZO_FIJO_BANK, record.source().id(),
					BankPlazoFijoWriter.naturalKey(record), CAPTURE).orElseThrow();
			assertDecimalEquals(record.nonClientTna(), row.rate());
			assertDecimalEquals(record.clientTna(), row.clientRate());
			assertThat(row.basis()).isEqualTo("TNA");
			assertThat(row.details().path("terms").size()).isEqualTo(record.terms().size());
			for (int index = 0; index < record.terms().size(); index++) {
				JsonNode term = row.details().path("terms").get(index);
				TermRate expected = record.terms().get(index);
				assertDecimalEquals(expected.tna(), jsonDecimal(term.path("tna")));
				assertDecimalEquals(expected.minAmount(), jsonDecimal(term.path("minAmount")));
				assertDecimalEquals(expected.maxAmount(), jsonDecimal(term.path("maxAmount")));
				assertThat(jsonText(term.path("minDays"))).isEqualTo(text(expected.minDays()));
				assertThat(jsonText(term.path("maxDays"))).isEqualTo(text(expected.maxDays()));
			}
		}
	}

	@Test
	void fundWriterStoresTheWholeQuote() {
		List<FundQuote> records = fundRecords();
		assertThat(records).isNotEmpty();
		WriteResult result = fundWriter.write(records, CAPTURE);

		assertThat(result.rows()).isEqualTo(records.size());
		assertThat(reader.fundQuoteCount()).isEqualTo(records.size());

		for (FundQuote record : records) {
			FundQuoteRow row = fundRowOf(record);
			assertDecimalEquals(record.vcp(), row.vcp());
			assertDecimalEquals(record.ccp(), row.ccp());
			assertDecimalEquals(record.patrimonio(), row.patrimonio());
			assertThat(row.quoteDate()).isEqualTo(record.date());
			assertThat(row.horizon()).isEqualTo(record.horizon());
			assertThat(row.confidence()).isEqualTo(Confidence.AGGREGATOR_AUTOMATIC.name());
			assertThat(row.fetchedAt()).isEqualTo(record.fetchedAt());
			assertThat(row.invalid()).isEqualTo(record.isInvalid());
			assertThat(row.flags()).containsExactlyElementsOf(record.flags());
		}

		FundQuote mercadoFondo = records.stream()
				.filter(record -> "Mercado Fondo - Clase A".equals(record.name()))
				.findFirst()
				.orElseThrow();
		assertThat(fundRowOf(mercadoFondo).vcp()).isEqualByComparingTo("25937.047");
	}

	@Test
	void cryptoWriterStoresApyAndItsOwnDate() {
		List<CryptoYield> records = cryptoRecords();
		assertThat(records).isNotEmpty();
		WriteResult result = cryptoWriter.write(records, CAPTURE);

		assertThat(result.rows()).isEqualTo(records.size());
		assertThat(reader.snapshotCount(InstrumentKind.CRYPTO_YIELD)).isEqualTo(records.size());

		for (CryptoYield record : records) {
			SnapshotRow row = reader.snapshot(InstrumentKind.CRYPTO_YIELD, record.source().id(),
					CryptoYieldWriter.naturalKey(record), CAPTURE).orElseThrow();
			assertDecimalEquals(record.apy(), row.rate());
			assertThat(row.basis()).isEqualTo("APY");
			assertThat(row.dataDate()).isEqualTo(record.date());
			assertThat(row.details().path("platform").stringValue()).isEqualTo(record.platform());
			assertThat(row.details().path("currency").stringValue()).isEqualTo(record.currency());
		}
	}

	@Test
	void macroWriterStoresValuesByVariableAndDate() {
		List<MacroObservation> records = macroRecords();
		assertThat(records).isNotEmpty();
		WriteResult result = macroWriter.write(records, CAPTURE);

		assertThat(result.rows()).isEqualTo(records.size());
		assertThat(reader.macroCount()).isEqualTo(records.size());

		for (MacroObservation record : records) {
			StoreReader.MacroRow row = reader.macro(record.variable(), record.date(), record.source().id()).orElseThrow();
			assertDecimalEquals(record.value(), row.value());
			assertThat(row.confidence()).isEqualTo(record.confidence().name());
			assertThat(row.fetchedAt()).isEqualTo(record.fetchedAt());
			assertThat(row.flags()).containsExactlyElementsOf(record.flags());
		}
	}

	@Test
	void dollarWriterStoresBuySellAndSourceInstant() {
		DollarQuote record = dollarRecord();
		WriteResult result = dollarWriter.write(List.of(record), CAPTURE);

		assertThat(result.rows()).isEqualTo(1);
		StoreReader.DollarRow row = reader.dollar(record.source().id(), CAPTURE).orElseThrow();
		assertThat(row.buy()).isEqualByComparingTo("1534.2");
		assertThat(row.sell()).isEqualByComparingTo("1539.4");
		assertThat(row.updatedAt()).isEqualTo(Instant.parse("2026-10-13T20:57:00Z"));
		assertThat(row.fetchedAt()).isEqualTo(record.fetchedAt());
		assertThat(row.confidence()).isEqualTo(Confidence.AGGREGATOR_AUTOMATIC.name());
		assertDecimalEquals(record.buy(), row.buy());
		assertDecimalEquals(record.sell(), row.sell());
		assertThat(row.flags()).containsExactlyElementsOf(record.flags());
	}

	@Test
	void walletWriterStoresBaseTnaScheduleAndCuratedVerifiedAt() {
		WalletRate uala = UalaWalletAdapter.parseExtract(Fixtures.bytes("uala/cuenta-remunerada-extract.json"), FETCHED);
		WalletRate curated = CuratedWalletRateAdapter.parse(curatedSeed(), FETCHED).get(0);
		WriteResult result = walletWriter.write(List.of(uala, curated), CAPTURE);

		assertThat(result.rows()).isEqualTo(2);
		assertThat(reader.snapshotCount(InstrumentKind.WALLET)).isEqualTo(2);

		SnapshotRow ualaRow = reader.snapshot(InstrumentKind.WALLET, uala.source().id(),
				WalletRateWriter.naturalKey(uala), CAPTURE).orElseThrow();
		assertThat(ualaRow.rate()).isEqualByComparingTo("0.19");
		assertThat(ualaRow.basis()).isEqualTo("TNA");
		assertThat(ualaRow.dataDate()).isEqualTo(CAPTURE);
		assertThat(ualaRow.verifiedAt()).isNull();
		assertThat(ualaRow.confidence()).isEqualTo(Confidence.SCRAPED_ENTITY_WEB.name());
		assertThat(ualaRow.details().path("tiers")).hasSize(1);
		assertThat(ualaRow.details().path("tiers").get(0).path("floor").decimalValue()).isEqualByComparingTo("10000");
		assertThat(ualaRow.details().path("tiers").get(0).path("ceiling").decimalValue())
				.isEqualByComparingTo("1000000");
		assertThat(ualaRow.details().path("tiers").get(0).path("tna").decimalValue()).isEqualByComparingTo("0.19");
		assertThat(ualaRow.details().path("conditions")).hasSize(2);
		assertThat(ualaRow.details().path("conditions").get(0).path("condition").stringValue())
				.isEqualTo("Si acumulás $50.000 en ciertas operaciones este mes");
		assertThat(ualaRow.details().path("conditions").get(1).path("tiers").get(0).path("tna").decimalValue())
				.isEqualByComparingTo("0.24");

		SnapshotRow curatedRow = reader.snapshot(InstrumentKind.WALLET, curated.source().id(),
				WalletRateWriter.naturalKey(curated), CAPTURE).orElseThrow();
		assertThat(curatedRow.rate()).isEqualByComparingTo("0.20");
		assertThat(curatedRow.verifiedAt()).isEqualTo(LocalDate.parse("2026-10-13"));
		assertThat(curatedRow.dataDate()).isEqualTo(LocalDate.parse("2026-10-13"));
		assertThat(curatedRow.confidence()).isEqualTo(Confidence.CURATED_MANUAL.name());
		assertThat(curatedRow.details().path("tiers").get(0).path("ceiling").decimalValue())
				.isEqualByComparingTo("1000000");
	}

	@Test
	void sourceRunWriterRecordsEachSourceOncePerDate() {
		sourceRunWriter.record(CAPTURE, OfficialPlazoFijoWriter.SOURCE_ID, RunMode.REPLAY, RunStatus.OK, 33, null);
		SourceRunRow row = reader.sourceRun(CAPTURE, OfficialPlazoFijoWriter.SOURCE_ID).orElseThrow();
		assertThat(row.mode()).isEqualTo("REPLAY");
		assertThat(row.status()).isEqualTo("OK");
		assertThat(row.rows()).isEqualTo(33);
		assertThat(row.failure()).isNull();

		sourceRunWriter.record(CAPTURE, OfficialPlazoFijoWriter.SOURCE_ID, RunMode.REPLAY, RunStatus.DEGRADED, 0,
				"secondary source failed");

		SourceRunRow updated = reader.sourceRun(CAPTURE, OfficialPlazoFijoWriter.SOURCE_ID).orElseThrow();
		assertThat(updated.status()).isEqualTo("DEGRADED");
		assertThat(updated.rows()).isZero();
		assertThat(updated.failure()).isEqualTo("secondary source failed");
		assertThat(reader.sourceRun(CAPTURE, OfficialPlazoFijoWriter.SOURCE_ID)).isPresent();
		assertThat(countSourceRuns(CAPTURE)).isEqualTo(1);
	}

	@Test
	void rerunningACaptureDateLeavesOneSnapshotPerKeyAndUpdatesValues() {
		List<OfficialPlazoFijoRate> records = officialRecords();
		officialWriter.write(records, CAPTURE);
		int distinct = (int) records.stream().map(OfficialPlazoFijoWriter::naturalKey).distinct().count();
		assertThat(reader.snapshotCount(InstrumentKind.PLAZO_FIJO_OFFICIAL)).isEqualTo(distinct);

		OfficialPlazoFijoRate target = records.get(0);
		officialWriter.write(List.of(withTea(target, "0.99")), CAPTURE);

		assertThat(reader.snapshotCount(InstrumentKind.PLAZO_FIJO_OFFICIAL)).isEqualTo(distinct);
		assertThat(snapshotOf(target).rate()).isEqualByComparingTo("0.99");
	}

	@Test
	void fundAndMacroQuotesUpsertOnTheirOwnDate() {
		FundQuote quote = fundRecords().get(0);
		fundWriter.write(List.of(quote), CAPTURE);
		fundWriter.write(List.of(copyWithVcp(quote, "1234.5")), CAPTURE);
		assertThat(reader.fundQuoteCount()).isEqualTo(1);
		assertThat(fundRowOf(quote).vcp()).isEqualByComparingTo("1234.5");

		List<MacroObservation> macros = macroRecords();
		MacroObservation observation = macros.get(macros.size() - 1);
		macroWriter.write(List.of(observation), CAPTURE);
		macroWriter.write(List.of(copyWithValue(observation, "0.021")), CAPTURE);
		assertThat(reader.macroCount()).isEqualTo(1);
		StoreReader.MacroRow row = reader.macro(observation.variable(), observation.date(), observation.source().id())
				.orElseThrow();
		assertThat(row.value()).isEqualByComparingTo("0.021");
	}

	@Test
	void invalidRowsAreStoredWithInvalidTrue() {
		List<OfficialPlazoFijoRate> records = officialRecords();
		List<OfficialPlazoFijoRate> invalid = records.stream().filter(OfficialPlazoFijoRate::isInvalid).toList();
		assertThat(invalid).isNotEmpty();
		officialWriter.write(records, CAPTURE);

		for (OfficialPlazoFijoRate record : invalid) {
			SnapshotRow row = snapshotOf(record);
			assertThat(row.invalid()).isTrue();
			assertDecimalEquals(record.tea(), row.rate());
			assertThat(row.flags()).containsExactlyElementsOf(record.flags());
		}

		List<FundQuote> quotes = fundRecords();
		FundQuote zeroPrice = withQuote(quotes.get(0), "0", List.of(invalidFlag()));
		FundQuote warned = withQuote(quotes.get(1), quotes.get(1).vcp().toPlainString(), List.of(warningFlag()));
		fundWriter.write(List.of(zeroPrice, warned), CAPTURE);

		FundQuoteRow invalidRow = fundRowOf(zeroPrice);
		assertThat(invalidRow.invalid()).isTrue();
		assertThat(invalidRow.vcp()).isEqualByComparingTo("0");
		assertThat(invalidRow.flags()).containsExactly(invalidFlag());

		FundQuoteRow warnedRow = fundRowOf(warned);
		assertThat(warnedRow.invalid()).isFalse();
		assertDecimalEquals(warned.vcp(), warnedRow.vcp());
		assertThat(warnedRow.flags()).containsExactly(warningFlag());
	}

	@Test
	void duplicateNaturalKeysKeepTheFirstAndAreCounted() {
		OfficialPlazoFijoRate first = officialRecords().stream().filter(record -> !record.isInvalid()).findFirst()
				.orElseThrow();
		WriteResult result = officialWriter.write(List.of(first, withTea(first, "0.42")), CAPTURE);

		assertThat(result.rows()).isEqualTo(1);
		assertThat(result.duplicates()).isEqualTo(1);
		assertThat(result.duplicateKeys()).containsExactly(OfficialPlazoFijoWriter.naturalKey(first));
		assertThat(result.failureText()).contains("kept-first");
		assertThat(reader.snapshotCount(InstrumentKind.PLAZO_FIJO_OFFICIAL)).isEqualTo(1);
		assertDecimalEquals(first.tea(), snapshotOf(first).rate());
	}

	@Test
	void firstSeenAndLastSeenWidenOnly() {
		OfficialPlazoFijoRate record = officialRecords().get(0);
		officialWriter.write(List.of(record), LocalDate.parse("2026-10-14"));
		officialWriter.write(List.of(record), LocalDate.parse("2026-10-10"));

		InstrumentRow widened = instrumentOf(record);
		assertThat(widened.firstSeen()).isEqualTo(LocalDate.parse("2026-10-10"));
		assertThat(widened.lastSeen()).isEqualTo(LocalDate.parse("2026-10-14"));

		officialWriter.write(List.of(record), LocalDate.parse("2026-10-12"));

		InstrumentRow unchanged = instrumentOf(record);
		assertThat(unchanged.firstSeen()).isEqualTo(LocalDate.parse("2026-10-10"));
		assertThat(unchanged.lastSeen()).isEqualTo(LocalDate.parse("2026-10-14"));
	}

	private static List<OfficialPlazoFijoRate> officialRecords() {
		return BcraPlazoFijoAdapter.parse(Fixtures.bytes("bcra/transparencia-plazos-fijos.json"), FETCHED);
	}

	private static List<BankPlazoFijoRate> bankRecords() {
		return ArgentinaDatosPlazoFijoAdapter.parse(Fixtures.bytes("argentinadatos/plazo-fijo.json"), FETCHED);
	}

	private static List<FundQuote> fundRecords() {
		return ArgentinaDatosFundAdapter.parse(
				Fixtures.bytes("argentinadatos/fci-mercado-dinero-ultimo.json"), FETCHED);
	}

	private static List<CryptoYield> cryptoRecords() {
		return ArgentinaDatosCryptoAdapter.parse(Fixtures.bytes("argentinadatos/rendimientos.json"), FETCHED);
	}

	private static List<MacroObservation> macroRecords() {
		return ArgentinaDatosInflationAdapter.parse(Fixtures.bytes("argentinadatos/inflacion.json"), FETCHED);
	}

	private static DollarQuote dollarRecord() {
		return DolarApiAdapter.parse(Fixtures.bytes("dolarapi/dolar-bolsa.json"), FETCHED);
	}

	private static byte[] curatedSeed() {
		try (InputStream in = StoreWriterTest.class.getResourceAsStream("/curated-wallet-rates.json")) {
			if (in == null) {
				throw new IllegalStateException("Missing /curated-wallet-rates.json");
			}
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private SnapshotRow snapshotOf(OfficialPlazoFijoRate record) {
		return reader.snapshot(InstrumentKind.PLAZO_FIJO_OFFICIAL, record.source().id(),
				OfficialPlazoFijoWriter.naturalKey(record), CAPTURE).orElseThrow();
	}

	private FundQuoteRow fundRowOf(FundQuote record) {
		return reader.fundQuote(record.source().id(), FundQuoteWriter.naturalKey(record), record.date()).orElseThrow();
	}

	private InstrumentRow instrumentOf(OfficialPlazoFijoRate record) {
		return reader.instrument(InstrumentKind.PLAZO_FIJO_OFFICIAL, record.source().id(),
				OfficialPlazoFijoWriter.naturalKey(record)).orElseThrow();
	}

	private int countSourceRuns(LocalDate captureDate) {
		return reader.sourceRunCount(captureDate);
	}

	private static void assertDecimalEquals(BigDecimal expected, BigDecimal actual) {
		if (expected == null) {
			assertThat(actual).isNull();
			return;
		}
		assertThat(actual).isEqualByComparingTo(expected);
	}

	/** The text of a stored JSON node, or {@code null} when the node is absent or JSON null. */
	private static String jsonText(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}
		return node.isString() ? node.stringValue() : node.toString();
	}

	/** The decimal of a stored JSON node, or {@code null} when the node is absent or JSON null. */
	private static BigDecimal jsonDecimal(JsonNode node) {
		return node == null || node.isMissingNode() || node.isNull() ? null : node.decimalValue();
	}

	private static String text(Object value) {
		return value == null ? null : value.toString();
	}

	private static ValidationFlag invalidFlag() {
		return new ValidationFlag(FlagCode.INVALID_QUOTE, Severity.INVALID, "vcp is missing or not positive");
	}

	private static ValidationFlag warningFlag() {
		return new ValidationFlag(FlagCode.STALE, Severity.WARNING, "Data date is older than 7 days");
	}

	private static OfficialPlazoFijoRate withTea(OfficialPlazoFijoRate record, String tea) {
		return new OfficialPlazoFijoRate(record.source(), record.entityCode(), record.entityName(), record.productName(),
				record.segment(), record.termCode(), record.minimumAmount(), record.channel(),
				tea == null ? null : new BigDecimal(tea), record.dataDate(), record.fetchedAt(), record.confidence(),
				record.flags());
	}

	private static FundQuote withQuote(FundQuote quote, String vcp, List<ValidationFlag> flags) {
		return new FundQuote(quote.source(), quote.name(), quote.fund(), quote.shareClass(), quote.horizon(),
				quote.segment(), quote.date(), new BigDecimal(vcp), quote.ccp(), quote.patrimonio(), quote.fetchedAt(),
				quote.confidence(), flags);
	}

	private static FundQuote copyWithVcp(FundQuote quote, String vcp) {
		return withQuote(quote, vcp, quote.flags());
	}

	private static MacroObservation copyWithValue(MacroObservation observation, String value) {
		return new MacroObservation(observation.source(), observation.variable(), observation.date(),
				new BigDecimal(value), observation.fetchedAt(), observation.confidence(), observation.flags());
	}
}
