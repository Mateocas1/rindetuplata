package ar.rindetuplata.ingest;

import ar.rindetuplata.source.SourceException;
import ar.rindetuplata.source.crypto.ArgentinaDatosCryptoAdapter;
import ar.rindetuplata.source.crypto.CryptoYieldSource;
import ar.rindetuplata.source.dollar.DolarApiAdapter;
import ar.rindetuplata.source.dollar.DollarQuote;
import ar.rindetuplata.source.dollar.DollarSource;
import ar.rindetuplata.source.fund.ArgentinaDatosFundAdapter;
import ar.rindetuplata.source.fund.FundSource;
import ar.rindetuplata.source.macro.MacroObservation;
import ar.rindetuplata.source.macro.MacroSource;
import ar.rindetuplata.source.macro.MacroVariable;
import ar.rindetuplata.source.plazofijo.ArgentinaDatosPlazoFijoAdapter;
import ar.rindetuplata.source.plazofijo.BcraPlazoFijoAdapter;
import ar.rindetuplata.source.plazofijo.PlazoFijoReport;
import ar.rindetuplata.source.plazofijo.PlazoFijoSource;
import ar.rindetuplata.source.wallet.UalaWalletAdapter;
import ar.rindetuplata.source.wallet.WalletRate;
import ar.rindetuplata.source.wallet.WalletRateSource;
import ar.rindetuplata.store.BankPlazoFijoWriter;
import ar.rindetuplata.store.CryptoYieldWriter;
import ar.rindetuplata.store.DollarQuoteWriter;
import ar.rindetuplata.store.FundQuoteWriter;
import ar.rindetuplata.store.MacroObservationWriter;
import ar.rindetuplata.store.OfficialPlazoFijoWriter;
import ar.rindetuplata.store.RunMode;
import ar.rindetuplata.store.RunStatus;
import ar.rindetuplata.store.SourceRunWriter;
import ar.rindetuplata.store.WalletRateWriter;
import ar.rindetuplata.store.WriteResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.springframework.stereotype.Component;

/**
 * The body of every step of {@code dailyIngestionJob}, one method per data kind. Each method obtains its records
 * (fetch live or replay the T9 archive), writes them and records the {@code source_run} row of every source.
 *
 * <p>A {@link SourceException} is the failure of one source: it is recorded as {@code FAILED} and never blocks the
 * other sources or kinds (S5). Any other exception is a defect and fails its step, so the job becomes restartable.
 * A kind that the archive does not hold (funds, macro, dollar) is skipped in {@link RunMode#REPLAY}.
 */
@Component
public class IngestionSteps {

	static final String OFFICIAL_SOURCE = BcraPlazoFijoAdapter.SOURCE.id();
	static final String BANKS_SOURCE = ArgentinaDatosPlazoFijoAdapter.SOURCE.id();
	static final String FUNDS_SOURCE = ArgentinaDatosFundAdapter.SOURCE.id();
	static final String CRYPTO_SOURCE = ArgentinaDatosCryptoAdapter.SOURCE.id();
	static final String DOLLAR_SOURCE = DolarApiAdapter.SOURCE.id();
	static final String UALA_SOURCE = UalaWalletAdapter.SOURCE.id();

	private final PlazoFijoSource plazoFijoSource;

	private final FundSource fundSource;

	private final CryptoYieldSource cryptoYieldSource;

	private final MacroSource macroSource;

	private final DollarSource dollarSource;

	private final List<WalletRateSource> walletRateSources;

	private final OfficialPlazoFijoWriter officialWriter;

	private final BankPlazoFijoWriter bankWriter;

	private final FundQuoteWriter fundWriter;

	private final CryptoYieldWriter cryptoWriter;

	private final MacroObservationWriter macroWriter;

	private final DollarQuoteWriter dollarWriter;

	private final WalletRateWriter walletWriter;

	private final SourceRunWriter sourceRunWriter;

	private final ArchiveReader archive;

	public IngestionSteps(PlazoFijoSource plazoFijoSource, FundSource fundSource, CryptoYieldSource cryptoYieldSource,
			MacroSource macroSource, DollarSource dollarSource, List<WalletRateSource> walletRateSources,
			OfficialPlazoFijoWriter officialWriter, BankPlazoFijoWriter bankWriter, FundQuoteWriter fundWriter,
			CryptoYieldWriter cryptoWriter, MacroObservationWriter macroWriter, DollarQuoteWriter dollarWriter,
			WalletRateWriter walletWriter, SourceRunWriter sourceRunWriter, ArchiveReader archive) {
		this.plazoFijoSource = plazoFijoSource;
		this.fundSource = fundSource;
		this.cryptoYieldSource = cryptoYieldSource;
		this.macroSource = macroSource;
		this.dollarSource = dollarSource;
		this.walletRateSources = List.copyOf(walletRateSources);
		this.officialWriter = officialWriter;
		this.bankWriter = bankWriter;
		this.fundWriter = fundWriter;
		this.cryptoWriter = cryptoWriter;
		this.macroWriter = macroWriter;
		this.dollarWriter = dollarWriter;
		this.walletWriter = walletWriter;
		this.sourceRunWriter = sourceRunWriter;
		this.archive = archive;
	}

	/** Plazo fijo: official BCRA products and ArgentinaDatos bank rates, live or replayed. */
	public void plazoFijo(LocalDate dataDate, RunMode mode) {
		if (mode == RunMode.REPLAY) {
			replayPart(dataDate, OFFICIAL_SOURCE, BcraPlazoFijoAdapter::parse, officialWriter::write);
			replayPart(dataDate, BANKS_SOURCE, ArgentinaDatosPlazoFijoAdapter::parse, bankWriter::write);
			return;
		}
		livePlazoFijo(dataDate);
	}

	/** Money-market fund quotes: the latest business day and the one before it: live only (not archived). */
	public void funds(LocalDate dataDate, RunMode mode) {
		if (mode == RunMode.REPLAY) {
			return;
		}
		try {
			WriteResult latest = fundWriter.write(fundSource.fetchLatest(), dataDate);
			WriteResult previous = fundWriter.write(fundSource.fetchPrevious(), dataDate);
			record(dataDate, FUNDS_SOURCE, mode, RunStatus.OK, latest.rows() + previous.rows(),
					join(latest.failureText(), previous.failureText()));
		}
		catch (SourceException e) {
			recordFailure(dataDate, mode, e);
		}
	}

	/** Crypto and fintech yields per platform and currency, live or replayed. */
	public void crypto(LocalDate dataDate, RunMode mode) {
		if (mode == RunMode.REPLAY) {
			replayPart(dataDate, CRYPTO_SOURCE, ArgentinaDatosCryptoAdapter::parse, cryptoWriter::write);
			return;
		}
		try {
			WriteResult result = cryptoWriter.write(cryptoYieldSource.fetch(), dataDate);
			record(dataDate, CRYPTO_SOURCE, mode, RunStatus.OK, result.rows(), result.failureText());
		}
		catch (SourceException e) {
			recordFailure(dataDate, mode, e);
		}
	}

	/** Macro series (inflation, REM, PSP benchmark): live only (not archived). One failing variable keeps the rest. */
	public void macro(LocalDate dataDate, RunMode mode) {
		if (mode == RunMode.REPLAY) {
			return;
		}
		Map<String, List<MacroObservation>> bySource = new LinkedHashMap<>();
		Map<String, String> failures = new LinkedHashMap<>();
		for (MacroVariable variable : MacroVariable.values()) {
			try {
				for (MacroObservation observation : macroSource.fetch(variable)) {
					bySource.computeIfAbsent(observation.source().id(), key -> new ArrayList<>()).add(observation);
				}
			}
			catch (SourceException e) {
				failures.put(e.source().id(), e.getMessage());
			}
		}
		for (Map.Entry<String, List<MacroObservation>> entry : bySource.entrySet()) {
			WriteResult result = macroWriter.write(entry.getValue(), dataDate);
			record(dataDate, entry.getKey(), mode, RunStatus.OK, result.rows(), result.failureText());
		}
		failures.forEach((sourceId, reason) -> record(dataDate, sourceId, mode, RunStatus.FAILED, 0, reason));
	}

	/** Dólar MEP: live only (not archived). */
	public void dollar(LocalDate dataDate, RunMode mode) {
		if (mode == RunMode.REPLAY) {
			return;
		}
		try {
			DollarQuote quote = dollarSource.fetchMep();
			WriteResult result = dollarWriter.write(List.of(quote), dataDate);
			record(dataDate, DOLLAR_SOURCE, mode, RunStatus.OK, result.rows(), result.failureText());
		}
		catch (SourceException e) {
			recordFailure(dataDate, mode, e);
		}
	}

	/** Every injected wallet rate source (S12): live fetches all of them, replay only the archived Ualá extract. */
	public void wallets(LocalDate dataDate, RunMode mode) {
		if (mode == RunMode.REPLAY) {
			replayWallets(dataDate);
			return;
		}
		Map<String, List<WalletRate>> bySource = new LinkedHashMap<>();
		Map<String, String> failures = new LinkedHashMap<>();
		for (WalletRateSource source : walletRateSources) {
			try {
				for (WalletRate rate : source.fetch()) {
					bySource.computeIfAbsent(rate.source().id(), key -> new ArrayList<>()).add(rate);
				}
			}
			catch (SourceException e) {
				failures.put(e.source().id(), e.getMessage());
			}
		}
		for (Map.Entry<String, List<WalletRate>> entry : bySource.entrySet()) {
			WriteResult result = walletWriter.write(entry.getValue(), dataDate);
			record(dataDate, entry.getKey(), mode, RunStatus.OK, result.rows(), result.failureText());
		}
		failures.forEach((sourceId, reason) -> record(dataDate, sourceId, mode, RunStatus.FAILED, 0, reason));
	}

	private void livePlazoFijo(LocalDate dataDate) {
		PlazoFijoReport report;
		try {
			report = plazoFijoSource.fetch();
		}
		catch (SourceException e) {
			record(dataDate, OFFICIAL_SOURCE, RunMode.LIVE, RunStatus.FAILED, 0, e.getMessage());
			record(dataDate, BANKS_SOURCE, RunMode.LIVE, RunStatus.FAILED, 0, e.getMessage());
			return;
		}
		Map<String, String> failures = new LinkedHashMap<>();
		report.failures().forEach(failure -> failures.put(failure.source().id(), failure.reason()));
		String degraded = failures.isEmpty() ? null
				: "another source of the composite failed: " + String.join(", ", failures.keySet());
		writeCompositePart(dataDate, OFFICIAL_SOURCE, report.official(), failures, degraded, officialWriter::write);
		writeCompositePart(dataDate, BANKS_SOURCE, report.banks(), failures, degraded, bankWriter::write);
	}

	private <T> void writeCompositePart(LocalDate dataDate, String sourceId, List<T> records,
			Map<String, String> failures, String degraded, BiFunction<List<T>, LocalDate, WriteResult> write) {
		String ownFailure = failures.get(sourceId);
		if (ownFailure != null) {
			record(dataDate, sourceId, RunMode.LIVE, RunStatus.FAILED, 0, ownFailure);
			return;
		}
		WriteResult result = write.apply(records, dataDate);
		if (degraded == null) {
			record(dataDate, sourceId, RunMode.LIVE, RunStatus.OK, result.rows(), result.failureText());
			return;
		}
		record(dataDate, sourceId, RunMode.LIVE, RunStatus.DEGRADED, result.rows(),
				join(degraded, result.failureText()));
	}

	/** Replays one archived source: {@code MISSING_IN_ARCHIVE} when the manifest does not mark it {@code ok}. */
	private <T> void replayPart(LocalDate dataDate, String sourceId, BiFunction<byte[], Instant, List<T>> parse,
			BiFunction<List<T>, LocalDate, WriteResult> write) {
		ArchiveReader.Manifest manifest = archive.load(dataDate);
		if (!manifest.ok(sourceId)) {
			record(dataDate, sourceId, RunMode.REPLAY, RunStatus.MISSING_IN_ARCHIVE, 0, null);
			return;
		}
		List<T> records = parse.apply(archive.body(dataDate, sourceId), manifest.capturedAt());
		WriteResult result = write.apply(records, dataDate);
		record(dataDate, sourceId, RunMode.REPLAY, RunStatus.OK, result.rows(), result.failureText());
	}

	private void replayWallets(LocalDate dataDate) {
		ArchiveReader.Manifest manifest = archive.load(dataDate);
		if (!manifest.ok(UALA_SOURCE)) {
			record(dataDate, UALA_SOURCE, RunMode.REPLAY, RunStatus.MISSING_IN_ARCHIVE, 0, null);
			return;
		}
		WalletRate rate = UalaWalletAdapter.parseExtract(archive.body(dataDate, UALA_SOURCE), manifest.capturedAt());
		WriteResult result = walletWriter.write(List.of(rate), dataDate);
		record(dataDate, UALA_SOURCE, RunMode.REPLAY, RunStatus.OK, result.rows(), result.failureText());
	}

	private void recordFailure(LocalDate dataDate, RunMode mode, SourceException e) {
		record(dataDate, e.source().id(), mode, RunStatus.FAILED, 0, e.getMessage());
	}

	private void record(LocalDate dataDate, String sourceId, RunMode mode, RunStatus status, int rows, String failure) {
		sourceRunWriter.record(dataDate, sourceId, mode, status, rows, failure);
	}

	private static String join(String... parts) {
		List<String> texts = new ArrayList<>(parts.length);
		for (String part : parts) {
			if (part != null && !part.isBlank()) {
				texts.add(part);
			}
		}
		return texts.isEmpty() ? null : String.join("; ", texts);
	}
}
