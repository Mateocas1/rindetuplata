package ar.rindetuplata.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * The return an instrument actually delivered over one closed month, from the history stored for it (T6a, L29 a).
 * Nothing here is a projection: every rule says which stored data a month needs and disqualifies the instrument
 * when that data is missing, instead of guessing it.
 *
 * <ul>
 *   <li>{@link #wallet} and {@link #crypto} compound daily: each calendar day of the month earns the daily rate of the
 *       latest capture on or before that day, as long as that capture is at most {@value #MAX_CAPTURE_GAP_DAYS} days
 *       old. A day with no such capture disqualifies the instrument for the month.</li>
 *   <li>{@link #plazoFijo} locks the rate of the first capture of the month, which must be dated on or before the
 *       month's first business day, as one simple term over the month's days: TNA · days / 365.</li>
 *   <li>{@link #fund} is the vcp of the last quote on or before the month end over the vcp of the last quote on or
 *       before the previous month end, each at most {@value #FUND_WINDOW_DAYS} days before its end.</li>
 * </ul>
 *
 * <p>An {@link Outcome} keeps {@link Money#MATH} precision; {@link Realized#of} rounds to {@value #SCALE} decimals
 * {@link RoundingMode#HALF_EVEN} and derives the real return {@code (1 + nominal) / (1 + inflation) − 1}.
 */
public final class MonthlyYield {

    /** Decimals of a published nominal or real return (a fraction: 2% is {@code 0.02}). */
    public static final int SCALE = 6;

    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    /** The oldest a capture may be, in days, to still be the known rate of a day. */
    public static final int MAX_CAPTURE_GAP_DAYS = 3;

    /** How many days before a month end the last fund quote may be. */
    public static final int FUND_WINDOW_DAYS = 4;

    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(AnnualRate.DAY_COUNT);

    private MonthlyYield() {
    }

    /** A rate captured on a day: a TNA for wallets and plazo fijo, an APY for crypto, as fractions. */
    public record Capture(LocalDate date, BigDecimal rate) {
    }

    /** A fund class quote: the cuotaparte value of a day. */
    public record Quote(LocalDate date, BigDecimal vcp) {
    }

    /** Why a month cannot be measured for an instrument. */
    public enum Disqualification {
        /** No rate was known on the first day (or, for a plazo fijo, by the first business day). */
        NOT_CAPTURED_AT_START,
        /** A later day had no capture within {@value MonthlyYield#MAX_CAPTURE_GAP_DAYS} days. */
        GAP_IN_CAPTURES,
        /** No quote within {@value MonthlyYield#FUND_WINDOW_DAYS} days before the previous month end. */
        MISSING_BASE_QUOTE,
        /** No quote within {@value MonthlyYield#FUND_WINDOW_DAYS} days before the month end. */
        MISSING_END_QUOTE
    }

    /**
     * The measurement of one instrument for one month: the exact nominal return and the days of the month it covers,
     * or the reason it could not be measured ({@code nominal} is then {@code null}).
     */
    public record Outcome(BigDecimal nominal, int coveredDays, Disqualification disqualification) {

        static Outcome measured(BigDecimal nominal, int coveredDays) {
            return new Outcome(nominal, coveredDays, null);
        }

        static Outcome disqualified(Disqualification reason) {
            return new Outcome(null, 0, reason);
        }
    }

    /** A nominal and a real return rounded for publication, and whether the instrument beat inflation. */
    public record Realized(BigDecimal nominal, BigDecimal real, boolean beatInflation) {

        /**
         * {@code real = (1 + nominal) / (1 + inflation) − 1}, both rounded to {@value MonthlyYield#SCALE} decimals half-even.
         * Beating inflation means a real return above zero <em>as published</em>: a gain that rounds to
         * 0.000000 is not reported as a win.
         */
        public static Realized of(BigDecimal nominal, BigDecimal monthlyInflation) {
            BigDecimal real = round(Inflation.realReturn(nominal, monthlyInflation));
            return new Realized(round(nominal), real, real.signum() > 0);
        }
    }

    /** A wallet or account: {@code Capture.rate} is the TNA, the daily rate is {@code TNA / 365}. */
    public static Outcome wallet(List<Capture> captures, YearMonth month) {
        return dailyCompounding(captures, month, tna -> new AnnualRate(tna).dailyRate());
    }

    /** A crypto yield: {@code Capture.rate} is the APY, the daily rate is {@code (1 + APY)^(1/365) − 1}. */
    public static Outcome crypto(List<Capture> captures, YearMonth month) {
        return dailyCompounding(captures, month, apy -> Inflation.overHorizon(apy, 1));
    }

    /** A bank plazo fijo: {@code Capture.rate} is the non-client TNA. */
    public static Outcome plazoFijo(List<Capture> captures, YearMonth month) {
        LocalDate first = captures.stream()
                .map(Capture::date)
                .filter(date -> !date.isBefore(month.atDay(1)) && !date.isAfter(month.atEndOfMonth()))
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (first == null || first.isAfter(firstBusinessDay(month))) {
            return Outcome.disqualified(Disqualification.NOT_CAPTURED_AT_START);
        }
        Capture locked = captures.stream()
                .filter(capture -> capture.date().equals(first))
                .reduce((earlier, later) -> later)
                .orElseThrow();
        int days = month.lengthOfMonth();
        BigDecimal nominal = locked.rate().multiply(BigDecimal.valueOf(days), Money.MATH)
                .divide(DAYS_IN_YEAR, Money.MATH);
        return Outcome.measured(nominal, days);
    }

    /** A fund class: the ratio of the vcp of two month ends. {@code coveredDays} counts the month's days it spans. */
    public static Outcome fund(List<Quote> quotes, YearMonth month) {
        LocalDate previousEnd = month.minusMonths(1).atEndOfMonth();
        Quote base = lastQuoteWithin(quotes, previousEnd);
        if (base == null) {
            return Outcome.disqualified(Disqualification.MISSING_BASE_QUOTE);
        }
        Quote end = lastQuoteWithin(quotes, month.atEndOfMonth());
        if (end == null) {
            return Outcome.disqualified(Disqualification.MISSING_END_QUOTE);
        }
        BigDecimal nominal = end.vcp().divide(base.vcp(), Money.MATH).subtract(BigDecimal.ONE, Money.MATH);
        return Outcome.measured(nominal, Math.toIntExact(ChronoUnit.DAYS.between(previousEnd, end.date())));
    }

    /** The first Monday-to-Friday day of the month (holidays are not modelled). */
    public static LocalDate firstBusinessDay(YearMonth month) {
        LocalDate day = month.atDay(1);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.plusDays(1);
        }
        return day;
    }

    private static Quote lastQuoteWithin(List<Quote> quotes, LocalDate end) {
        LocalDate oldest = end.minusDays(FUND_WINDOW_DAYS);
        return quotes.stream()
                .filter(quote -> quote.vcp().signum() > 0 && !quote.date().isAfter(end)
                        && !quote.date().isBefore(oldest))
                .max(Comparator.comparing(Quote::date))
                .orElse(null);
    }

    private static Outcome dailyCompounding(List<Capture> captures, YearMonth month,
            UnaryOperator<BigDecimal> dailyRate) {
        List<Capture> sorted = new ArrayList<>(captures);
        sorted.sort(Comparator.comparing(Capture::date));
        Map<BigDecimal, BigDecimal> dailyByRate = new HashMap<>();
        BigDecimal factor = BigDecimal.ONE;
        int latest = -1;
        LocalDate start = month.atDay(1);
        for (LocalDate day = start; !day.isAfter(month.atEndOfMonth()); day = day.plusDays(1)) {
            while (latest + 1 < sorted.size() && !sorted.get(latest + 1).date().isAfter(day)) {
                latest++;
            }
            if (latest < 0 || ChronoUnit.DAYS.between(sorted.get(latest).date(), day) > MAX_CAPTURE_GAP_DAYS) {
                return Outcome.disqualified(day.equals(start)
                        ? Disqualification.NOT_CAPTURED_AT_START
                        : Disqualification.GAP_IN_CAPTURES);
            }
            BigDecimal daily = dailyByRate.computeIfAbsent(sorted.get(latest).rate(), dailyRate);
            factor = factor.multiply(BigDecimal.ONE.add(daily), Money.MATH);
        }
        return Outcome.measured(factor.subtract(BigDecimal.ONE, Money.MATH), month.lengthOfMonth());
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }
}
