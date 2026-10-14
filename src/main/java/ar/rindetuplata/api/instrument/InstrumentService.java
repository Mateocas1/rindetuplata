package ar.rindetuplata.api.instrument;

import ar.rindetuplata.api.ApiException;
import ar.rindetuplata.api.JsonScales;
import ar.rindetuplata.api.instrument.InstrumentQueryRepository.Header;
import ar.rindetuplata.api.instrument.InstrumentQueryRepository.QuoteRow;
import ar.rindetuplata.domain.FundYield;
import ar.rindetuplata.store.InstrumentKind;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The rules of the two instrument endpoints: the default and maximum windows of the history (measured in the
 * Argentine day the injected clock reports), the fund yield implied by consecutive quotes, and the INDEC monthly
 * inflation series that accompanies every history. Reading is delegated to {@link InstrumentQueryRepository}.
 */
@Service
public class InstrumentService {

	/** The default window: 90 calendar days including today, ending today in America/Argentina/Buenos_Aires. */
	public static final int DEFAULT_WINDOW_DAYS = 90;

	/** The longest window a client may ask for, counted inclusively. */
	public static final int MAX_WINDOW_DAYS = 366;

	/** The label of the monthly index the history carries (BCRA variable 27, the INDEC CPI, S10). */
	public static final String INDEC_CPI_MONTHLY = "INDEC_CPI_MONTHLY";

	private final InstrumentQueryRepository repository;
	private final Clock clock;

	public InstrumentService(InstrumentQueryRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public List<InstrumentSummary> list(InstrumentKind kind) {
		return repository.summaries(kind);
	}

	public InstrumentHistory history(long id, LocalDate from, LocalDate to) {
		Header instrument = repository.header(id)
				.orElseThrow(() -> ApiException.notFound("id", "Unknown instrument: " + id));
		LocalDate rangeTo = to == null ? LocalDate.now(clock) : to;
		LocalDate rangeFrom = from == null ? rangeTo.minusDays(DEFAULT_WINDOW_DAYS - 1L) : from;
		checkRange(rangeFrom, rangeTo);

		boolean fund = instrument.kind() == InstrumentKind.FUND_CLASS;
		List<SnapshotPoint> points = fund ? List.of() : repository.snapshots(id, rangeFrom, rangeTo);
		List<FundQuotePoint> quotes = fund ? fundQuotes(id, rangeFrom, rangeTo) : List.of();
		List<MonthlyInflationPoint> inflation = repository.monthlyInflation(rangeFrom, rangeTo);

		return new InstrumentHistory(Long.toString(instrument.id()), instrument.kind().name(),
				instrument.displayName(), instrument.entityName(), instrument.segment(), instrument.sourceId(), rangeFrom,
				rangeTo, points, quotes, inflation);
	}

	private List<FundQuotePoint> fundQuotes(long id, LocalDate from, LocalDate to) {
		List<QuoteRow> quotes = repository.quotes(id, from, to);
		QuoteRow previous = repository.quoteBefore(id, from).orElse(null);
		List<FundQuotePoint> points = new ArrayList<>(quotes.size());
		for (QuoteRow quote : quotes) {
			BigDecimal tna = previous == null ? null : tnaEquivalent(previous, quote);
			points.add(new FundQuotePoint(quote.date(), JsonScales.money(quote.vcp()), tna));
			previous = quote;
		}
		return points;
	}

	/** The TNA-equivalent between two quotes, or {@code null} when the vcp fell: a losing class has no yield. */
	private static BigDecimal tnaEquivalent(QuoteRow previous, QuoteRow current) {
		if (current.vcp().compareTo(previous.vcp()) < 0) {
			return null;
		}
		FundYield yield = new FundYield(previous.date(), previous.vcp(), current.date(), current.vcp());
		return JsonScales.rate(yield.tna().fraction());
	}

	private static void checkRange(LocalDate from, LocalDate to) {
		if (from.isAfter(to)) {
			throw ApiException.badRequest("from",
					"Parameter 'from' must not be after 'to': from=" + from + ", to=" + to);
		}
		long days = ChronoUnit.DAYS.between(from, to) + 1;
		if (days > MAX_WINDOW_DAYS) {
			throw ApiException.badRequest("to", "The date range must not exceed " + MAX_WINDOW_DAYS
					+ " days: from=" + from + ", to=" + to + " spans " + days + " days");
		}
	}
}
