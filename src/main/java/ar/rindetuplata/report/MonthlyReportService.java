package ar.rindetuplata.report;

import ar.rindetuplata.domain.MonthlyYield;
import ar.rindetuplata.domain.MonthlyYield.Capture;
import ar.rindetuplata.domain.MonthlyYield.Outcome;
import ar.rindetuplata.domain.MonthlyYield.Realized;
import ar.rindetuplata.ranking.DataVersion;
import ar.rindetuplata.ranking.ExcludedReason;
import ar.rindetuplata.ranking.FundClassRules;
import ar.rindetuplata.ranking.RankedKind;
import ar.rindetuplata.report.MonthlyReportRepository.FundSeries;
import ar.rindetuplata.report.MonthlyReportRepository.RateSeries;
import ar.rindetuplata.source.wallet.FciBackedWalletCatalog;
import ar.rindetuplata.source.wallet.FciBackedWalletMapping;
import ar.rindetuplata.source.wallet.FundClassKey;
import ar.rindetuplata.store.InstrumentKind;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The monthly "who beat inflation" report (T6a, L29 a). A month is reportable only when it has ended (in the Argentine
 * day the injected clock reports), INDEC has published its inflation and at least one instrument has the stored data
 * to be measured over the whole month. Each instrument's nominal return comes from {@link MonthlyYield}; the real
 * return is measured against the INDEC rate of the same month. Fund classes are grouped to their best class and an
 * FCI-backed wallet is reported once, as the wallet, the way the ranking does.
 *
 * <p>Building a month reads the stored history around it, which grows with every backfill, so the answers are cached
 * (T4a, L33 a) in bounded in-process LRUs: the month list under the {@linkplain DataVersion data version} and
 * today's date (a month closes at midnight), a month's report under the data version and the month (a closed month
 * stays closed). The version is read once per call before anything is computed, so an entry is never labelled newer
 * than the data it holds; a write that lands in between only makes the next call recompute. A failure is never
 * cached, and concurrent misses of one key compute independently (the results are equal, the last write wins).
 */
@Service
public class MonthlyReportService {

	private static final String FCI_WALLET_SOURCE = "fci-backed-wallets";

	private static final Comparator<ReportRow> BEST_REAL_FIRST = Comparator
			.comparing((ReportRow row) -> row.realized().real()).reversed()
			.thenComparing(ReportRow::name)
			.thenComparing(ReportRow::id);

	private static final Comparator<ReportRow> BEST_NOMINAL_FIRST = Comparator
			.comparing((ReportRow row) -> row.realized().nominal()).reversed()
			.thenComparing(ReportRow::name)
			.thenComparing(ReportRow::id);

	private static final Comparator<LeftOut> BY_NAME = Comparator.comparing(LeftOut::name).thenComparing(LeftOut::id);

	/** A month in the list: its inflation and how many instruments were measured and beat it. */
	public record MonthSummary(YearMonth month, MonthlyInflation inflation, int ranked, int beatInflation) {
	}

	private final MonthlyReportRepository repository;
	private final FciBackedWalletCatalog fciWallets;
	private final Clock clock;
	private final DataVersion dataVersion;
	private final LruCache<ListKey, List<MonthSummary>> monthLists;
	private final LruCache<ReportKey, MonthlyReport> reports;

	private record ListKey(String dataVersion, LocalDate today) {
	}

	private record ReportKey(String dataVersion, YearMonth month) {
	}

	public MonthlyReportService(MonthlyReportRepository repository, FciBackedWalletCatalog fciWallets, Clock clock,
			DataVersion dataVersion,
			@Value("${rindetuplata.report.cache-max-entries}") int cacheMaxEntries) {
		this.repository = repository;
		this.fciWallets = fciWallets;
		this.clock = clock;
		this.dataVersion = dataVersion;
		this.monthLists = new LruCache<>(cacheMaxEntries);
		this.reports = new LruCache<>(cacheMaxEntries);
	}

	/** The reportable months, newest first. */
	public List<MonthSummary> months() {
		ListKey key = new ListKey(dataVersion.current(), LocalDate.now(clock));
		return monthLists.get(key).orElseGet(() -> {
			List<MonthSummary> built = List.copyOf(buildMonths());
			monthLists.put(key, built);
			return built;
		});
	}

	private List<MonthSummary> buildMonths() {
		Optional<LocalDate> earliest = repository.earliestHistory();
		if (earliest.isEmpty()) {
			return List.of();
		}
		List<MonthSummary> months = new ArrayList<>();
		for (Map.Entry<YearMonth, MonthlyInflation> inflation : repository.inflation().entrySet()) {
			YearMonth month = inflation.getKey();
			if (!isClosed(month) || month.atEndOfMonth().isBefore(earliest.get())) {
				continue;
			}
			MonthlyReport report = build(month, inflation.getValue());
			if (!report.rows().isEmpty()) {
				months.add(new MonthSummary(month, inflation.getValue(), report.rows().size(),
						report.beatInflation()));
			}
		}
		return months;
	}

	/** The report of a month, or {@link MonthlyReportUnavailableException} saying which condition fails. */
	public MonthlyReport report(YearMonth month) {
		ReportKey key = new ReportKey(dataVersion.current(), month);
		Optional<MonthlyReport> cached = reports.get(key);
		if (cached.isPresent()) {
			return cached.get();
		}
		MonthlyReport built = buildReport(month);
		reports.put(key, built);
		return built;
	}

	private MonthlyReport buildReport(YearMonth month) {
		if (!isClosed(month)) {
			throw new MonthlyReportUnavailableException(MonthlyReportUnavailableException.Reason.NOT_CLOSED,
					"Month " + month + " is not a closed month: its report appears after it ends");
		}
		MonthlyInflation inflation = repository.inflation(month).orElseThrow(() -> new MonthlyReportUnavailableException(
				MonthlyReportUnavailableException.Reason.NO_INFLATION,
				"No INDEC monthly inflation is stored for " + month));
		MonthlyReport report = build(month, inflation);
		if (report.rows().isEmpty()) {
			throw new MonthlyReportUnavailableException(MonthlyReportUnavailableException.Reason.NO_COVERAGE,
					"The stored history does not cover " + month + ": no instrument has the data to be measured "
							+ "over the whole month");
		}
		return report;
	}

	private boolean isClosed(YearMonth month) {
		return month.atEndOfMonth().isBefore(LocalDate.now(clock));
	}

	private MonthlyReport build(YearMonth month, MonthlyInflation inflation) {
		List<ReportRow> rows = new ArrayList<>();
		List<LeftOut> leftOut = new ArrayList<>();
		for (RateSeries series : repository.rateSeries(month)) {
			rateInstrument(series, month, inflation, rows, leftOut);
		}
		fundInstruments(repository.fundSeries(month), month, inflation, rows, leftOut);
		rows.sort(BEST_REAL_FIRST);
		leftOut.sort(BY_NAME);
		return new MonthlyReport(month, inflation, rows, leftOut);
	}

	private void rateInstrument(RateSeries series, YearMonth month, MonthlyInflation inflation, List<ReportRow> rows,
			List<LeftOut> leftOut) {
		RankedKind kind = rankedKind(series.kind());
		List<Capture> captures = series.snapshots().stream()
				.filter(snapshot -> !snapshot.invalid() && snapshot.rate() != null && snapshot.rate().signum() > 0)
				.map(snapshot -> new Capture(snapshot.date(), snapshot.rate()))
				.toList();
		if (captures.isEmpty()) {
			boolean noNonClientRate = series.kind() == InstrumentKind.PLAZO_FIJO_BANK
					&& series.snapshots().stream().allMatch(snapshot -> !snapshot.invalid() && snapshot.rate() == null);
			leftOut.add(new LeftOut(Long.toString(series.id()), kind, series.name(), series.entity(),
					series.sourceId(), noNonClientRate ? LeftOutReason.NOT_OFFERED_TO_NON_CLIENTS
							: LeftOutReason.INVALID_DATA));
			return;
		}
		Outcome outcome = switch (series.kind()) {
			case PLAZO_FIJO_BANK -> MonthlyYield.plazoFijo(captures, month);
			case WALLET -> MonthlyYield.wallet(captures, month);
			case CRYPTO_YIELD -> MonthlyYield.crypto(captures, month);
			default -> throw new IllegalStateException("Not a rate instrument: " + series.kind());
		};
		measure(outcome, Long.toString(series.id()), kind, series.name(), series.entity(), series.sourceId(), month,
				inflation, rows, leftOut);
	}

	private void fundInstruments(List<FundSeries> classes, YearMonth month, MonthlyInflation inflation,
			List<ReportRow> rows, List<LeftOut> leftOut) {
		for (FciBackedWalletMapping mapping : fciWallets.mappings()) {
			Optional<FundSeries> winner = classes.stream().filter(fund -> mapping.matches(fund.name())).findFirst();
			if (winner.isEmpty()) {
				leftOut.add(new LeftOut(mapping.walletId(), RankedKind.WALLET, mapping.name(), mapping.fundClass(),
						FCI_WALLET_SOURCE, LeftOutReason.FUND_CLASS_NOT_FOUND));
				continue;
			}
			measure(MonthlyYield.fund(winner.get().quotes(), month), Long.toString(winner.get().id()),
					RankedKind.WALLET, mapping.name(), mapping.fundClass(), winner.get().sourceId(), month,
					inflation, rows, leftOut);
		}
		Map<String, ReportRow> bestOfFund = new LinkedHashMap<>();
		Map<String, Integer> classesOfFund = new LinkedHashMap<>();
		for (FundSeries fund : classes) {
			if (fciWallets.mappings().stream().anyMatch(mapping -> mapping.matches(fund.name()))) {
				continue;
			}
			Optional<ExcludedReason> unavailable = FundClassRules.exclusion(fund.name());
			if (unavailable.isPresent()) {
				leftOut.add(new LeftOut(Long.toString(fund.id()), RankedKind.FUND, fund.name(), fund.entity(),
						fund.sourceId(), LeftOutReason.valueOf(unavailable.get().name())));
				continue;
			}
			List<ReportRow> measured = new ArrayList<>(1);
			measure(MonthlyYield.fund(fund.quotes(), month), Long.toString(fund.id()), RankedKind.FUND, fund.name(),
					fund.entity(), fund.sourceId(), month, inflation, measured, leftOut);
			for (ReportRow row : measured) {
				String key = FundClassKey.fundOf(fund.name());
				classesOfFund.merge(key, 1, Integer::sum);
				bestOfFund.merge(key, row, MonthlyReportService::better);
			}
		}
		bestOfFund.forEach((key, best) -> rows.add(best.withFoldedClasses(classesOfFund.get(key) - 1)));
	}

	/** The class of a fund with the higher return; a tie keeps the one that sorts first by name, then id. */
	private static ReportRow better(ReportRow a, ReportRow b) {
		return BEST_NOMINAL_FIRST.compare(a, b) <= 0 ? a : b;
	}

	private static void measure(Outcome outcome, String id, RankedKind kind, String name, String entity,
			String sourceId, YearMonth month, MonthlyInflation inflation, List<ReportRow> rows,
			List<LeftOut> leftOut) {
		if (outcome.disqualification() != null) {
			leftOut.add(new LeftOut(id, kind, name, entity, sourceId,
					LeftOutReason.valueOf(outcome.disqualification().name())));
			return;
		}
		Realized realized = Realized.of(outcome.nominal(), inflation.value());
		rows.add(new ReportRow(id, kind, name, entity, sourceId, realized, outcome.coveredDays(),
				month.lengthOfMonth(), 0));
	}

	private static RankedKind rankedKind(InstrumentKind kind) {
		return switch (kind) {
			case PLAZO_FIJO_BANK -> RankedKind.PLAZO_FIJO;
			case WALLET -> RankedKind.WALLET;
			case CRYPTO_YIELD -> RankedKind.CRYPTO;
			default -> throw new IllegalStateException("Not a rate instrument: " + kind);
		};
	}
}
