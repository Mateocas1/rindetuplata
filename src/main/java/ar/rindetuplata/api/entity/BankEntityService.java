package ar.rindetuplata.api.entity;

import ar.rindetuplata.api.ApiException;
import ar.rindetuplata.api.JsonScales;
import ar.rindetuplata.api.entity.BankEntityRepository.BankRow;
import ar.rindetuplata.api.entity.BankEntityRepository.LatestRow;
import ar.rindetuplata.api.instrument.InstrumentService;
import ar.rindetuplata.entity.BankSlugs;
import ar.rindetuplata.entity.BankStanding;
import ar.rindetuplata.entity.BankStanding.Observation;
import ar.rindetuplata.entity.BankStanding.Standing;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * The rules of the bank entity endpoints: slugs over every bank (so a bank's slug never depends on whether it has
 * data today), the latest valid rates, and the 90-day window (the instrument history's default, measured in the
 * Argentine day the injected clock reports) behind the series and the best-rate count.
 */
@Service
public class BankEntityService {

	private final BankEntityRepository repository;
	private final Clock clock;

	public BankEntityService(BankEntityRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/** The banks with a valid snapshot, sorted by slug. */
	public List<BankRatesView> banks() {
		LocalDate today = LocalDate.now(clock);
		List<BankRow> banks = repository.banks();
		Map<Long, String> slugs = slugs(banks);
		Map<Long, LatestRow> latest = latestById(today);
		return banks.stream()
				.filter(bank -> latest.containsKey(bank.id()))
				.map(bank -> rates(bank, slugs.get(bank.id()), latest.get(bank.id())))
				.sorted(Comparator.comparing(BankRatesView::slug))
				.toList();
	}

	public BankDetailView bank(String slug) {
		LocalDate today = LocalDate.now(clock);
		List<BankRow> banks = repository.banks();
		Map<Long, String> slugs = slugs(banks);
		Map<Long, LatestRow> latest = latestById(today);
		BankRow bank = banks.stream()
				.filter(candidate -> slug.equals(slugs.get(candidate.id())) && latest.containsKey(candidate.id()))
				.findFirst()
				.orElseThrow(() -> ApiException.notFound("slug", "Unknown bank: " + slug));

		LocalDate from = today.minusDays(InstrumentService.DEFAULT_WINDOW_DAYS - 1L);
		List<Observation> observations = repository.observations(from, today);
		Standing standing = BankStanding.of(bank.id(), observations);
		List<BankSeriesPoint> series = observations.stream()
				.filter(observation -> observation.instrumentId() == bank.id())
				.map(observation -> new BankSeriesPoint(observation.date(), JsonScales.rate(observation.rate())))
				.toList();

		LatestRow row = latest.get(bank.id());
		return new BankDetailView(slug, bank.name(), Long.toString(bank.id()), bank.sourceId(),
				JsonScales.rate(row.nonClientTna()), JsonScales.rate(row.clientTna()), row.captureDate(),
				row.dataDate(), from, today, series, standing.bestDays(), standing.observedDays());
	}

	private static BankRatesView rates(BankRow bank, String slug, LatestRow row) {
		return new BankRatesView(slug, bank.name(), Long.toString(bank.id()), bank.sourceId(),
				JsonScales.rate(row.nonClientTna()), JsonScales.rate(row.clientTna()), row.captureDate(),
				row.dataDate());
	}

	private static Map<Long, String> slugs(List<BankRow> banks) {
		return BankSlugs.assign(banks.stream().map(bank -> new BankSlugs.Named(bank.id(), bank.name())).toList());
	}

	private Map<Long, LatestRow> latestById(LocalDate today) {
		Map<Long, LatestRow> byId = new HashMap<>();
		for (LatestRow row : repository.latest(today)) {
			byId.put(row.instrumentId(), row);
		}
		return byId;
	}
}
