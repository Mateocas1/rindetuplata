package ar.rindetuplata.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ar.rindetuplata.ranking.DataVersion;
import ar.rindetuplata.report.MonthlyReportRepository.RateSeries;
import ar.rindetuplata.report.MonthlyReportRepository.Snapshot;
import ar.rindetuplata.source.wallet.FciBackedWalletCatalog;
import ar.rindetuplata.store.InstrumentKind;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code months} and {@code report(month)} are served from a bounded LRU keyed by the data version (and
 * by today's date for the list, since a month closes at midnight). The repository stands for the database: counting
 * its calls shows what was recomputed. A failure is never cached, and the cache never holds more than its cap.
 */
class MonthlyReportCacheTest {

	private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
	private static final YearMonth AUGUST = YearMonth.of(2026, 8);
	private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
	private static final YearMonth JULY = YearMonth.of(2026, 7);

	private final MonthlyReportRepository repository = mock(MonthlyReportRepository.class);
	private final DataVersion dataVersion = mock(DataVersion.class);
	private final TestClock clock = new TestClock(Instant.parse("2026-10-20T12:00:00Z"));
	/** Newest month first, the order the repository answers in. */
	private final Map<YearMonth, MonthlyInflation> inflation = new TreeMap<>(Comparator.reverseOrder());

	private MonthlyReportService service(int maxEntries) {
		return new MonthlyReportService(repository, FciBackedWalletCatalog.fromClasspath(), clock, dataVersion,
				maxEntries);
	}

	@BeforeEach
	void aHistoryOfThreeClosedMonthsAtVersionOne() {
		given(dataVersion.current()).willReturn("v1");
		given(repository.earliestHistory()).willReturn(Optional.of(LocalDate.parse("2026-06-01")));
		inflation.put(SEPTEMBER, indec(SEPTEMBER, "0.019"));
		inflation.put(AUGUST, indec(AUGUST, "0.018"));
		inflation.put(JULY, indec(JULY, "0.017"));
		given(repository.inflation()).willAnswer(call -> new LinkedHashMap<>(inflation));
		given(repository.inflation(any(YearMonth.class)))
				.willAnswer(call -> Optional.ofNullable(inflation.get(call.<YearMonth>getArgument(0))));
		given(repository.rateSeries(any(YearMonth.class)))
				.willAnswer(call -> List.of(wallet(call.getArgument(0))));
		given(repository.fundSeries(any(YearMonth.class))).willReturn(List.of());
	}

	private static MonthlyInflation indec(YearMonth month, String value) {
		return new MonthlyInflation(new BigDecimal(value), month.atEndOfMonth(), "OFFICIAL_AUTOMATIC",
				"bcra-estadisticas-monetarias");
	}

	/** One wallet captured every day of the month and the days before it, at a constant 30% TNA. */
	private static RateSeries wallet(YearMonth month) {
		List<Snapshot> snapshots = new ArrayList<>();
		for (LocalDate day = month.atDay(1).minusDays(3); !day.isAfter(month.atEndOfMonth()); day = day.plusDays(1)) {
			snapshots.add(new Snapshot(day, new BigDecimal("0.30"), false));
		}
		return new RateSeries(1L, InstrumentKind.WALLET, "Wallet", "Wallet", "uala", snapshots);
	}

	@Test
	void theMonthListIsBuiltOncePerDataVersionAndDay() {
		MonthlyReportService service = service(8);

		List<MonthlyReportService.MonthSummary> first = service.months();
		List<MonthlyReportService.MonthSummary> second = service.months();

		assertThat(first).extracting(MonthlyReportService.MonthSummary::month).containsExactly(SEPTEMBER, AUGUST, JULY);
		assertThat(second).isSameAs(first);
		verify(repository, times(1)).inflation();
		verify(repository, times(1)).rateSeries(SEPTEMBER);
	}

	@Test
	void aNewDataVersionRebuildsTheList() {
		MonthlyReportService service = service(8);
		service.months();

		given(dataVersion.current()).willReturn("v2");
		inflation.remove(JULY);

		assertThat(service.months()).extracting(MonthlyReportService.MonthSummary::month)
				.containsExactly(SEPTEMBER, AUGUST);
		verify(repository, times(2)).inflation();
	}

	@Test
	void aNewDayRebuildsTheListBecauseAMonthMayHaveClosed() {
		MonthlyReportService service = service(8);
		inflation.put(YearMonth.of(2026, 10), indec(YearMonth.of(2026, 10), "0.020"));
		assertThat(service.months()).extracting(MonthlyReportService.MonthSummary::month)
				.containsExactly(SEPTEMBER, AUGUST, JULY);

		clock.set(Instant.parse("2026-11-01T12:00:00Z"));

		assertThat(service.months()).extracting(MonthlyReportService.MonthSummary::month)
				.containsExactly(YearMonth.of(2026, 10), SEPTEMBER, AUGUST, JULY);
		verify(repository, times(2)).inflation();
	}

	@Test
	void aMonthReportIsBuiltOncePerDataVersion() {
		MonthlyReportService service = service(8);

		MonthlyReport first = service.report(SEPTEMBER);
		MonthlyReport second = service.report(SEPTEMBER);

		assertThat(second).isSameAs(first);
		assertThat(first.rows()).hasSize(1);
		verify(repository, times(1)).rateSeries(SEPTEMBER);

		given(dataVersion.current()).willReturn("v2");
		MonthlyReport afterWrite = service.report(SEPTEMBER);

		assertThat(afterWrite).isNotSameAs(first).isEqualTo(first);
		verify(repository, times(2)).rateSeries(SEPTEMBER);
	}

	@Test
	void aDayChangeDoesNotInvalidateAClosedMonthReport() {
		MonthlyReportService service = service(8);
		MonthlyReport first = service.report(SEPTEMBER);

		clock.set(Instant.parse("2026-10-25T12:00:00Z"));

		assertThat(service.report(SEPTEMBER)).isSameAs(first);
		verify(repository, times(1)).rateSeries(SEPTEMBER);
	}

	@Test
	void theReportCacheEvictsTheLeastRecentlyUsedMonthAtItsCap() {
		MonthlyReportService service = service(2);
		service.report(JULY);
		service.report(AUGUST);
		service.report(JULY);
		service.report(SEPTEMBER);

		service.report(JULY);
		service.report(SEPTEMBER);
		verify(repository, times(1)).rateSeries(JULY);
		verify(repository, times(1)).rateSeries(SEPTEMBER);

		service.report(AUGUST);
		verify(repository, times(2)).rateSeries(AUGUST);
	}

	@Test
	void aFailureIsNeverCachedAndIsThrownAgainWithTheSameReason() {
		MonthlyReportService service = service(8);
		YearMonth open = YearMonth.of(2026, 10);
		inflation.remove(AUGUST);

		for (int i = 0; i < 2; i++) {
			assertThatThrownBy(() -> service.report(open)).isInstanceOfSatisfying(MonthlyReportUnavailableException.class,
					e -> assertThat(e.reason()).isEqualTo(MonthlyReportUnavailableException.Reason.NOT_CLOSED));
			assertThatThrownBy(() -> service.report(AUGUST)).isInstanceOfSatisfying(
					MonthlyReportUnavailableException.class,
					e -> assertThat(e.reason()).isEqualTo(MonthlyReportUnavailableException.Reason.NO_INFLATION));
		}
		verify(repository, times(2)).inflation(AUGUST);

		inflation.put(AUGUST, indec(AUGUST, "0.018"));
		given(dataVersion.current()).willReturn("v2");

		assertThat(service.report(AUGUST).rows()).hasSize(1);
	}

	@Test
	void anEmptyHistoryIsCachedLikeAnyOtherAnswerUntilTheVersionMoves() {
		given(repository.earliestHistory()).willReturn(Optional.empty());
		MonthlyReportService service = service(8);

		assertThat(service.months()).isEmpty();
		assertThat(service.months()).isEmpty();
		verify(repository, times(1)).earliestHistory();

		given(dataVersion.current()).willReturn("v2");
		given(repository.earliestHistory()).willReturn(Optional.of(LocalDate.parse("2026-06-01")));

		assertThat(service.months()).hasSize(3);
		verify(repository, times(2)).earliestHistory();
	}

	@Test
	void theCacheCapMustBeAtLeastOne() {
		assertThatThrownBy(() -> service(0)).isInstanceOf(IllegalArgumentException.class);
	}

	/** A clock the test moves between calls. */
	private static final class TestClock extends Clock {

		private Instant instant;

		TestClock(Instant instant) {
			this.instant = instant;
		}

		void set(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZONE;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return Clock.fixed(instant, zone);
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
