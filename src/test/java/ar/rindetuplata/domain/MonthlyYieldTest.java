package ar.rindetuplata.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.domain.MonthlyYield.Capture;
import ar.rindetuplata.domain.MonthlyYield.Disqualification;
import ar.rindetuplata.domain.MonthlyYield.Outcome;
import ar.rindetuplata.domain.MonthlyYield.Quote;
import ar.rindetuplata.domain.MonthlyYield.Realized;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The realized nominal return of an instrument over a closed month, with hand-computed values: wallets
 * and crypto compound daily at the latest known rate of each day, a plazo fijo locks the rate of its first capture as
 * one term over the month's days, a fund is the vcp ratio of two month ends. Expected values come from an independent
 * high-precision computation, rounded half-even to six decimals.
 */
class MonthlyYieldTest {

	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

	private static LocalDate d(String iso) {
		return LocalDate.parse(iso);
	}

	private static Capture capture(String iso, String rate) {
		return new Capture(d(iso), new BigDecimal(rate));
	}

	private static Quote quote(String iso, String vcp) {
		return new Quote(d(iso), new BigDecimal(vcp));
	}

	/** One capture on every day of {@code first..last}, all at {@code rate}. */
	private static List<Capture> daily(String first, String last, String rate) {
		List<Capture> captures = new ArrayList<>();
		for (LocalDate day = d(first); !day.isAfter(d(last)); day = day.plusDays(1)) {
			captures.add(new Capture(day, new BigDecimal(rate)));
		}
		return captures;
	}

	private static BigDecimal rounded(BigDecimal value) {
		return value.setScale(MonthlyYield.SCALE, RoundingMode.HALF_EVEN);
	}

	// --- wallets: daily compounding at the latest known TNA ---------------------------------------------------

	@Test
	void aWalletAtAConstantTnaCompoundsItsDailyRateOverEveryDayOfTheMonth() {
		// TNA 36.5% is exactly 0.1% a day; October has 31 days: 1.001^31 - 1 = 0.031470.
		Outcome outcome = MonthlyYield.wallet(daily("2026-10-01", "2026-10-31", "0.365"), OCTOBER);

		assertThat(outcome.disqualification()).isNull();
		assertThat(outcome.coveredDays()).isEqualTo(31);
		assertThat(rounded(outcome.nominal())).isEqualByComparingTo("0.031470");
	}

	@Test
	void aWalletUsesTheLatestKnownRateOfEachDayAndAWeekendCarriesTheFridayRate() {
		// 10 days at 0.1% a day, then a capture on Oct 11 raises it to 0.2% a day for the other 21 days. Only the
		// change days are captured: every day in between takes the latest capture (gaps of at most 3 days are fine).
		List<Capture> captures = List.of(capture("2026-10-01", "0.365"), capture("2026-10-04", "0.365"),
				capture("2026-10-07", "0.365"), capture("2026-10-10", "0.365"), capture("2026-10-11", "0.73"),
				capture("2026-10-14", "0.73"), capture("2026-10-17", "0.73"), capture("2026-10-20", "0.73"),
				capture("2026-10-23", "0.73"), capture("2026-10-26", "0.73"), capture("2026-10-29", "0.73"));

		Outcome outcome = MonthlyYield.wallet(captures, OCTOBER);

		// Days 1-10 at 0.001, days 11-31 at 0.002: 1.001^10 * 1.002^21 - 1 = 0.053326.
		assertThat(outcome.disqualification()).isNull();
		assertThat(rounded(outcome.nominal())).isEqualByComparingTo("0.053326");
	}

	@Test
	void aCaptureFromBeforeTheMonthCoversItsFirstDaysWithinTheGapLimit() {
		// The last September capture is 2 days before Oct 1, so Oct 1 and Oct 2 still know a rate.
		List<Capture> captures = new ArrayList<>(daily("2026-10-03", "2026-10-31", "0.365"));
		captures.add(0, capture("2026-09-29", "0.365"));

		Outcome outcome = MonthlyYield.wallet(captures, OCTOBER);

		assertThat(outcome.disqualification()).isNull();
		assertThat(rounded(outcome.nominal())).isEqualByComparingTo("0.031470");
	}

	@Test
	void aGapOfExactlyThreeDaysWithoutACaptureIsTolerated() {
		// Captures on Oct 1, 5, 9, ... 29: on Oct 4 the latest known rate is exactly 3 days old.
		List<Capture> captures = new ArrayList<>();
		for (int day = 1; day <= 31; day += 4) {
			captures.add(new Capture(LocalDate.of(2026, 10, day), new BigDecimal("0.365")));
		}

		Outcome outcome = MonthlyYield.wallet(captures, OCTOBER);

		assertThat(outcome.disqualification()).isNull();
	}

	@Test
	void aGapOfMoreThanThreeDaysDisqualifiesTheInstrument() {
		// Last capture Oct 10; on Oct 14 the latest known rate is 4 days old.
		List<Capture> captures = new ArrayList<>(daily("2026-10-01", "2026-10-10", "0.365"));
		captures.addAll(daily("2026-10-15", "2026-10-31", "0.365"));

		Outcome outcome = MonthlyYield.wallet(captures, OCTOBER);

		assertThat(outcome.disqualification()).isEqualTo(Disqualification.GAP_IN_CAPTURES);
		assertThat(outcome.nominal()).isNull();
	}

	@Test
	void aWalletWithNoRateKnownOnTheFirstDayOfTheMonthIsNotCapturedAtStart() {
		// The history of this instrument starts on Oct 14: a half month is not compared with a month of inflation.
		Outcome outcome = MonthlyYield.wallet(daily("2026-10-14", "2026-10-31", "0.365"), OCTOBER);

		assertThat(outcome.disqualification()).isEqualTo(Disqualification.NOT_CAPTURED_AT_START);
		assertThat(outcome.nominal()).isNull();
	}

	@Test
	void aWalletThatStopsBeingCapturedBeforeTheEndOfTheMonthIsDisqualified() {
		Outcome outcome = MonthlyYield.wallet(daily("2026-10-01", "2026-10-20", "0.365"), OCTOBER);

		assertThat(outcome.disqualification()).isEqualTo(Disqualification.GAP_IN_CAPTURES);
	}

	@Test
	void noCaptureAtAllIsNotCapturedAtStart() {
		assertThat(MonthlyYield.wallet(List.of(), OCTOBER).disqualification())
				.isEqualTo(Disqualification.NOT_CAPTURED_AT_START);
	}

	// --- crypto: APY, daily compounding ------------------------------------------------------------------------

	@Test
	void aCryptoYieldIsAnApyCompoundedDailyAtItsDailyEquivalent() {
		// APY 50% over the 30 days of September: 1.5^(30/365) - 1 = 0.033887.
		Outcome outcome = MonthlyYield.crypto(daily("2026-09-01", "2026-09-30", "0.5"), YearMonth.of(2026, 9));

		assertThat(outcome.disqualification()).isNull();
		assertThat(outcome.coveredDays()).isEqualTo(30);
		assertThat(rounded(outcome.nominal())).isEqualByComparingTo("0.033887");
	}

	@Test
	void aCryptoYieldFollowsTheSameGapRuleAsAWallet() {
		List<Capture> captures = new ArrayList<>(daily("2026-09-01", "2026-09-10", "0.5"));
		captures.addAll(daily("2026-09-20", "2026-09-30", "0.5"));

		assertThat(MonthlyYield.crypto(captures, YearMonth.of(2026, 9)).disqualification())
				.isEqualTo(Disqualification.GAP_IN_CAPTURES);
	}

	// --- plazo fijo: the first capture locks the rate for the whole month ---------------------------------------

	@Test
	void aPlazoFijoLocksItsFirstCaptureAsOneSimpleTermOverTheMonthsDays() {
		// Oct 1 2026 is a Thursday, the first business day. TNA 30% over 31 days: 0.30 * 31 / 365 = 0.025479.
		// Later captures at other rates do not matter: the saver locked the rate at the start.
		List<Capture> captures = List.of(capture("2026-10-01", "0.30"), capture("2026-10-15", "0.50"));

		Outcome outcome = MonthlyYield.plazoFijo(captures, OCTOBER);

		assertThat(outcome.disqualification()).isNull();
		assertThat(outcome.coveredDays()).isEqualTo(31);
		assertThat(rounded(outcome.nominal())).isEqualByComparingTo("0.025479");
	}

	@Test
	void aPlazoFijoCapturedOnTheFirstBusinessDayWhenTheMonthStartsOnAWeekendStillCounts() {
		// Aug 1 2026 is a Saturday: the first business day is Monday Aug 3. A capture on Aug 3 locks the rate.
		YearMonth august = YearMonth.of(2026, 8);
		assertThat(MonthlyYield.firstBusinessDay(august)).isEqualTo(d("2026-08-03"));

		Outcome outcome = MonthlyYield.plazoFijo(List.of(capture("2026-08-03", "0.365")), august);

		assertThat(outcome.disqualification()).isNull();
		// 0.365 * 31 / 365 = 0.031 exactly.
		assertThat(rounded(outcome.nominal())).isEqualByComparingTo("0.031000");
	}

	@Test
	void aPlazoFijoFirstCapturedAfterTheFirstBusinessDayHasNoRateToLock() {
		Outcome outcome = MonthlyYield.plazoFijo(List.of(capture("2026-10-02", "0.30")), OCTOBER);

		assertThat(outcome.disqualification()).isEqualTo(Disqualification.NOT_CAPTURED_AT_START);
		assertThat(outcome.nominal()).isNull();
	}

	@Test
	void aPlazoFijoCaptureFromTheMonthBeforeDoesNotLockTheMonth() {
		Outcome outcome = MonthlyYield.plazoFijo(List.of(capture("2026-09-30", "0.30")), OCTOBER);

		assertThat(outcome.disqualification()).isEqualTo(Disqualification.NOT_CAPTURED_AT_START);
	}

	@Test
	void theFirstBusinessDayIsTheFirstDayWhenTheMonthStartsOnAWeekday() {
		assertThat(MonthlyYield.firstBusinessDay(OCTOBER)).isEqualTo(d("2026-10-01"));
		// Nov 1 2026 is a Sunday.
		assertThat(MonthlyYield.firstBusinessDay(YearMonth.of(2026, 11))).isEqualTo(d("2026-11-02"));
	}

	// --- funds: vcp month end to previous month end -------------------------------------------------------------

	@Test
	void aFundIsTheRatioOfItsLastQuoteOfTheMonthToItsLastQuoteOfThePreviousMonth() {
		// 100.000 on Sep 30, 102.500 on Oct 30 (the last quote of October is 1 day before the end).
		List<Quote> quotes = List.of(quote("2026-09-29", "99.000"), quote("2026-09-30", "100.000"),
				quote("2026-10-15", "101.000"), quote("2026-10-30", "102.500"));

		Outcome outcome = MonthlyYield.fund(quotes, OCTOBER);

		assertThat(outcome.disqualification()).isNull();
		assertThat(rounded(outcome.nominal())).isEqualByComparingTo("0.025000");
		// The ratio covers the days of October up to the last quote: Sep 30 -> Oct 30.
		assertThat(outcome.coveredDays()).isEqualTo(30);
	}

	@Test
	void aFundQuoteFourDaysBeforeTheEndOfTheMonthIsStillWithinTheWindow() {
		// Base 4 days before Sep 30 (Sep 26) and end 4 days before Oct 31 (Oct 27).
		List<Quote> quotes = List.of(quote("2026-09-26", "100.000"), quote("2026-10-27", "102.000"));

		Outcome outcome = MonthlyYield.fund(quotes, OCTOBER);

		assertThat(outcome.disqualification()).isNull();
		assertThat(rounded(outcome.nominal())).isEqualByComparingTo("0.020000");
		assertThat(outcome.coveredDays()).isEqualTo(27);
	}

	@Test
	void aFundWhoseBaseQuoteIsMoreThanFourDaysBeforeThePreviousMonthEndIsDisqualified() {
		List<Quote> quotes = List.of(quote("2026-09-25", "100.000"), quote("2026-10-31", "102.000"));

		Outcome outcome = MonthlyYield.fund(quotes, OCTOBER);

		assertThat(outcome.disqualification()).isEqualTo(Disqualification.MISSING_BASE_QUOTE);
		assertThat(outcome.nominal()).isNull();
	}

	@Test
	void aFundWhoseLastQuoteIsMoreThanFourDaysBeforeTheMonthEndIsDisqualified() {
		List<Quote> quotes = List.of(quote("2026-09-30", "100.000"), quote("2026-10-26", "102.000"));

		Outcome outcome = MonthlyYield.fund(quotes, OCTOBER);

		assertThat(outcome.disqualification()).isEqualTo(Disqualification.MISSING_END_QUOTE);
	}

	@Test
	void aQuoteAfterTheMonthEndIsNeverUsed() {
		List<Quote> quotes = List.of(quote("2026-09-30", "100.000"), quote("2026-10-31", "102.000"),
				quote("2026-11-02", "110.000"));

		assertThat(rounded(MonthlyYield.fund(quotes, OCTOBER).nominal())).isEqualByComparingTo("0.020000");
	}

	@Test
	void aFundThatLostValueHasANegativeNominalReturn() {
		List<Quote> quotes = List.of(quote("2026-09-30", "100.000"), quote("2026-10-31", "99.000"));

		assertThat(rounded(MonthlyYield.fund(quotes, OCTOBER).nominal())).isEqualByComparingTo("-0.010000");
	}

	// --- real return --------------------------------------------------------------------------------------------

	@Test
	void theRealReturnIsOnePlusNominalOverOnePlusInflationMinusOne() {
		// 1.025 / 1.02 - 1 = 0.004902
		Realized realized = Realized.of(new BigDecimal("0.025"), new BigDecimal("0.02"));

		assertThat(realized.nominal()).isEqualByComparingTo("0.025000");
		assertThat(realized.real()).isEqualByComparingTo("0.004902");
		assertThat(realized.beatInflation()).isTrue();
	}

	@Test
	void anInstrumentBelowInflationHasANegativeRealReturnAndDidNotBeatIt() {
		// 1.02 / 1.025 - 1 = -0.004878
		Realized realized = Realized.of(new BigDecimal("0.02"), new BigDecimal("0.025"));

		assertThat(realized.real()).isEqualByComparingTo("-0.004878");
		assertThat(realized.beatInflation()).isFalse();
	}

	@Test
	void matchingInflationExactlyIsNotBeatingIt() {
		Realized realized = Realized.of(new BigDecimal("0.02"), new BigDecimal("0.02"));

		assertThat(realized.real()).isEqualByComparingTo("0.000000");
		assertThat(realized.beatInflation()).isFalse();
	}

	@Test
	void aRealReturnThatRoundsToZeroIsNotReportedAsBeatingInflation() {
		// Real return 4e-8 shows as 0.000000 at six decimals, so it must not read as a win.
		Realized realized = Realized.of(new BigDecimal("0.02000008"), new BigDecimal("0.02"));

		assertThat(realized.real()).isEqualByComparingTo("0.000000");
		assertThat(realized.beatInflation()).isFalse();
	}

	@Test
	void theValuesAreRoundedHalfEvenToSixDecimals() {
		// Both are ties: 0.0123455 goes up to the even 6 and 0.0123465 stays on the even 6.
		assertThat(Realized.of(new BigDecimal("0.0123455"), BigDecimal.ZERO).nominal())
				.isEqualTo(new BigDecimal("0.012346"));
		assertThat(Realized.of(new BigDecimal("0.0123465"), BigDecimal.ZERO).nominal())
				.isEqualTo(new BigDecimal("0.012346"));
	}
}
