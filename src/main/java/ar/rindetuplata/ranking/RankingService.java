package ar.rindetuplata.ranking;

import ar.rindetuplata.domain.AnnualRate;
import ar.rindetuplata.domain.ConditionalRate;
import ar.rindetuplata.domain.Evaluation;
import ar.rindetuplata.domain.Ineligible;
import ar.rindetuplata.domain.Inflation;
import ar.rindetuplata.domain.Instrument;
import ar.rindetuplata.domain.Money;
import ar.rindetuplata.domain.MoneyMarketFund;
import ar.rindetuplata.domain.PlazoFijo;
import ar.rindetuplata.domain.Ranking;
import ar.rindetuplata.domain.RateTier;
import ar.rindetuplata.domain.RemuneratedAccount;
import ar.rindetuplata.domain.Simulation;
import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.ValidationFlag;
import ar.rindetuplata.source.wallet.FciBackedWalletCatalog;
import ar.rindetuplata.source.wallet.FciBackedWalletMapping;
import ar.rindetuplata.store.InstrumentKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * The catalog behind the public ranking (S15, T5 design notes): it turns the stored snapshots of each kind into
 * {@code domain} instruments, simulates them through {@link Ranking#of} for the user's amount and days, and returns
 * plain result records. Every instrument the user cannot reach is returned with its reason, never dropped.
 *
 * <p>Kinds never share a capture date: each kind (and each source within it) contributes its own latest snapshot,
 * so a source that failed today still shows its last good day with its own dates.
 */
@Service
public class RankingService {

	private final RankingReader reader;

	private final FciBackedWalletCatalog fciWallets;

	public RankingService(RankingReader reader, FciBackedWalletCatalog fciWallets) {
		this.reader = reader;
		this.fciWallets = fciWallets;
	}

	/** The personalized ranking: every eligible instrument simulated for the query, sorted by final amount. */
	public RankingResult rank(RankingQuery query) {
		Basket basket = collect(query.kinds(), query.asOf(), query.clientOf(), query.amount(), query.days());
		RankingReader.StoredInflation rem = reader.latestExpectedInflation(query.asOf()).orElse(null);
		Ranking domain = Ranking.of(instruments(basket), query.amount(), query.days(),
				rem == null ? BigDecimal.ZERO : rem.value());
		Map<Instrument, Candidate> byInstrument = byInstrument(basket);

		List<RankedEntry> ranked = new ArrayList<>(domain.ranked().size());
		for (Ranking.Entry entry : domain.ranked()) {
			ranked.add(entry(byInstrument.get(entry.instrument()), entry.simulation(),
					rem == null ? null : entry.realReturn()));
		}
		List<IneligibleEntry> ineligible = new ArrayList<>(domain.ineligible().size());
		for (Ranking.Rejected rejected : domain.ineligible()) {
			ineligible.add(ineligible(byInstrument.get(rejected.instrument()), rejected.ineligible()));
		}
		List<ExcludedEntry> excluded = new ArrayList<>(basket.excluded());
		excluded.sort(Comparator.comparing(ExcludedEntry::name));

		int total = ranked.size();
		List<RankedEntry> page = total <= query.limit() ? List.copyOf(ranked)
				: List.copyOf(ranked.subList(0, query.limit()));
		InflationView inflation = rem == null ? null : new InflationView(InflationView.REM_12M_EXPECTED, rem.value(),
				rem.observationDate(), domain.inflationOverHorizon());
		return new RankingResult(query, inflation, page, ineligible, excluded,
				new RankingTotals(total, ineligible.size(), excluded.size()));
	}

	/**
	 * Simulates one instrument by its public id for an amount and a term. The result is the same entry the ranking
	 * returns, the ineligible reason, or the exclusion reason; an id no ranked instrument carries is a typed
	 * not-found. {@code client} personalizes a bank with the user's client rate.
	 */
	public SimulationResult simulate(long instrumentId, Money amount, int days, boolean client) {
		String id = String.valueOf(instrumentId);
		LocalDate asOf = RankingQuery.today();
		Set<Long> clientOf = client ? Set.of(instrumentId) : Set.of();
		Basket basket = collect(EnumSet.allOf(RankedKind.class), asOf, clientOf, amount, days);

		for (Candidate candidate : basket.candidates()) {
			if (!candidate.id().equals(id)) {
				continue;
			}
			Evaluation evaluation = candidate.instrument().evaluate(amount, days);
			if (evaluation instanceof Simulation simulation) {
				BigDecimal realReturn = reader.latestExpectedInflation(asOf)
						.map(rem -> Inflation.realReturn(simulation.nominalReturn(),
								Inflation.overHorizon(rem.value(), days)))
						.orElse(null);
				return new SimulationResult.Simulated(entry(candidate, simulation, realReturn));
			}
			return new SimulationResult.IneligibleInstrument(id, candidate.name(), (Ineligible) evaluation);
		}
		for (ExcludedEntry entry : basket.excluded()) {
			if (entry.id().equals(id)) {
				return new SimulationResult.ExcludedInstrument(id, entry.name(), entry.reason(), entry.flags());
			}
		}
		throw new InstrumentNotFoundException(instrumentId);
	}

	private Basket collect(Set<RankedKind> kinds, LocalDate asOf, Set<Long> clientOf, Money amount, int days) {
		List<Candidate> candidates = new ArrayList<>();
		List<ExcludedEntry> excluded = new ArrayList<>();
		if (kinds.contains(RankedKind.PLAZO_FIJO)) {
			for (RankingReader.StoredRate row : reader.latestRates(InstrumentKind.PLAZO_FIJO_BANK, asOf)) {
				collectBank(row, clientOf, amount, days, candidates, excluded);
			}
		}
		Map<Long, List<RankingReader.StoredQuote>> funds = Map.of();
		if (kinds.contains(RankedKind.FUND) || kinds.contains(RankedKind.WALLET)) {
			funds = reader.fundSeries(asOf);
		}
		if (kinds.contains(RankedKind.WALLET)) {
			for (RankingReader.StoredRate row : reader.latestRates(InstrumentKind.WALLET, asOf)) {
				collectWallet(row, candidates, excluded);
			}
			for (FciBackedWalletMapping mapping : fciWallets.mappings()) {
				collectFciBackedWallet(mapping, funds, asOf, candidates, excluded);
			}
		}
		if (kinds.contains(RankedKind.FUND)) {
			// An FCI-backed wallet is the same money as its fund class: when wallets are ranked too, show it once.
			Set<String> walletClasses = kinds.contains(RankedKind.WALLET)
					? fciWallets.mappings().stream().map(FciBackedWalletMapping::fundClass).collect(Collectors.toSet())
					: Set.of();
			for (Map.Entry<Long, List<RankingReader.StoredQuote>> series : funds.entrySet()) {
				if (walletClasses.contains(series.getValue().get(0).displayName())) {
					continue;
				}
				collectFund(series.getKey(), series.getValue(), asOf, candidates, excluded);
			}
		}
		if (kinds.contains(RankedKind.CRYPTO)) {
			for (RankingReader.StoredRate row : reader.latestRates(InstrumentKind.CRYPTO_YIELD, asOf)) {
				collectCrypto(row, candidates, excluded);
			}
		}
		return new Basket(candidates, excluded);
	}

	private void collectBank(RankingReader.StoredRate row, Set<Long> clientOf, Money amount, int days,
			List<Candidate> candidates, List<ExcludedEntry> excluded) {
		String id = String.valueOf(row.instrumentId());
		RankedKind kind = RankedKind.PLAZO_FIJO;
		if (row.invalid()) {
			excluded.add(excluded(row, kind, ExcludedReason.INVALID_DATA));
			return;
		}
		RateUsed rateUsed;
		if (clientOf.contains(row.instrumentId())) {
			Optional<BigDecimal> band = InstrumentMapper.matchingTermTna(RankingJson.terms(row.details()), days, amount);
			BigDecimal chosen = band.orElse(row.clientRate());
			if (chosen == null) {
				excluded.add(excluded(row, kind, ExcludedReason.NO_CLIENT_RATE));
				return;
			}
			rateUsed = RateUsed.tna(chosen,
					band.isPresent() ? RateKind.CLIENT_TERM_BAND : RateKind.CLIENT);
		}
		else {
			if (row.rate() == null) {
				excluded.add(excluded(row, kind, ExcludedReason.NOT_OFFERED_TO_NON_CLIENTS));
				return;
			}
			rateUsed = RateUsed.tna(row.rate(), RateKind.NON_CLIENT);
		}
		Money minimum = row.minimumAmount() == null ? Money.ars("0") : Money.ars(row.minimumAmount());
		PlazoFijo plazoFijo = new PlazoFijo(id, row.displayName(), new AnnualRate(rateUsed.fraction()),
				PlazoFijo.DEFAULT_MINIMUM_DAYS, minimum);
		candidates.add(new Candidate(plazoFijo, kind, id, row.displayName(), row.entityName(), row.sourceId(),
				row.confidence(), row.verifiedAt(), row.captureDate(), row.dataDate(), rateUsed,
				warnings(row.flags())));
	}

	private void collectWallet(RankingReader.StoredRate row, List<Candidate> candidates,
			List<ExcludedEntry> excluded) {
		String id = String.valueOf(row.instrumentId());
		RankedKind kind = RankedKind.WALLET;
		if (row.invalid()) {
			excluded.add(excluded(row, kind, ExcludedReason.INVALID_DATA));
			return;
		}
		List<RateTier> tiers = InstrumentMapper.rateTiers(RankingJson.walletTiers(row.details()));
		if (tiers.isEmpty()) {
			excluded.add(excluded(row, kind, ExcludedReason.INVALID_DATA));
			return;
		}
		List<ConditionalRate> conditions = InstrumentMapper.conditionalRates(RankingJson.walletConditions(row.details()));
		RemuneratedAccount account = new RemuneratedAccount(id, row.displayName(), tiers, conditions);
		RateUsed rateUsed = RateUsed.tna(row.rate() == null ? BigDecimal.ZERO : row.rate(), RateKind.WALLET_BASE);
		candidates.add(new Candidate(account, kind, id, row.displayName(), row.entityName(), row.sourceId(),
				row.confidence(), row.verifiedAt(), row.captureDate(), row.dataDate(), rateUsed,
				warnings(row.flags())));
	}

	private void collectCrypto(RankingReader.StoredRate row, List<Candidate> candidates,
			List<ExcludedEntry> excluded) {
		if (!"ARS".equals(RankingJson.text(row.details(), "currency"))) {
			return;
		}
		String id = String.valueOf(row.instrumentId());
		RankedKind kind = RankedKind.CRYPTO;
		if (row.invalid() || row.rate() == null) {
			excluded.add(excluded(row, kind, ExcludedReason.INVALID_DATA));
			return;
		}
		AnnualRate tna = InstrumentMapper.apyToTna(row.rate());
		RemuneratedAccount account = new RemuneratedAccount(id, row.displayName(), List.of(RateTier.above(tna)),
				List.of());
		candidates.add(new Candidate(account, kind, id, row.displayName(), row.entityName(), row.sourceId(),
				row.confidence(), row.verifiedAt(), row.captureDate(), row.dataDate(),
				RateUsed.tna(tna.fraction(), RateKind.APY_TNA_EQUIVALENT), warnings(row.flags())));
	}

	private void collectFund(long instrumentId, List<RankingReader.StoredQuote> quotes, LocalDate asOf,
			List<Candidate> candidates, List<ExcludedEntry> excluded) {
		String id = String.valueOf(instrumentId);
		RankingReader.StoredQuote latest = quotes.get(0);
		FundEvaluator.FundEvaluation evaluation = FundEvaluator.evaluate(quotes, asOf);
		if (!evaluation.eligible()) {
			excluded.add(new ExcludedEntry(id, RankedKind.FUND, latest.displayName(),
					SourceRegistry.of(latest.sourceId()), evaluation.latestQuoteDate(), evaluation.latestQuoteDate(),
					evaluation.reason(), evaluation.warnings()));
			return;
		}
		MoneyMarketFund fund = new MoneyMarketFund(id, latest.displayName(), evaluation.yield().tna());
		candidates.add(new Candidate(fund, RankedKind.FUND, id, latest.displayName(), latest.entityName(),
				latest.sourceId(), latest.confidence(), null, evaluation.latestQuoteDate(),
				evaluation.latestQuoteDate(),
				RateUsed.tna(evaluation.yield().tna().fraction(), RateKind.FUND_TNA_EQUIVALENT),
				evaluation.warnings()));
	}

	private void collectFciBackedWallet(FciBackedWalletMapping mapping, Map<Long, List<RankingReader.StoredQuote>> funds,
			LocalDate asOf, List<Candidate> candidates, List<ExcludedEntry> excluded) {
		Optional<Map.Entry<Long, List<RankingReader.StoredQuote>>> match = funds.entrySet().stream()
				.filter(series -> mapping.fundClass().equals(series.getValue().get(0).displayName()))
				.findFirst();
		if (match.isEmpty()) {
			excluded.add(new ExcludedEntry(mapping.walletId(), RankedKind.WALLET, mapping.name(),
					SourceRegistry.FCI_BACKED_WALLETS, asOf, asOf, ExcludedReason.INSUFFICIENT_HISTORY, List.of()));
			return;
		}
		long instrumentId = match.get().getKey();
		List<RankingReader.StoredQuote> quotes = match.get().getValue();
		String id = String.valueOf(instrumentId);
		RankingReader.StoredQuote latest = quotes.get(0);
		FundEvaluator.FundEvaluation evaluation = FundEvaluator.evaluate(quotes, asOf);
		if (!evaluation.eligible()) {
			excluded.add(new ExcludedEntry(id, RankedKind.WALLET, mapping.name(),
					SourceRegistry.of(latest.sourceId()), evaluation.latestQuoteDate(), evaluation.latestQuoteDate(),
					evaluation.reason(), evaluation.warnings()));
			return;
		}
		MoneyMarketFund fund = new MoneyMarketFund(id, mapping.name(), evaluation.yield().tna());
		candidates.add(new Candidate(fund, RankedKind.WALLET, id, mapping.name(), mapping.fundClass(),
				latest.sourceId(), latest.confidence(), null, evaluation.latestQuoteDate(),
				evaluation.latestQuoteDate(),
				RateUsed.tna(evaluation.yield().tna().fraction(), RateKind.FUND_TNA_EQUIVALENT),
				evaluation.warnings()));
	}

	private ExcludedEntry excluded(RankingReader.StoredRate row, RankedKind kind, ExcludedReason reason) {
		return new ExcludedEntry(String.valueOf(row.instrumentId()), kind, row.displayName(),
				SourceRegistry.of(row.sourceId()), row.captureDate(), row.dataDate(), reason, row.flags());
	}

	private static List<ValidationFlag> warnings(List<ValidationFlag> flags) {
		return flags.stream().filter(flag -> flag.severity() == Severity.WARNING).toList();
	}

	private static List<Instrument> instruments(Basket basket) {
		return basket.candidates().stream().map(Candidate::instrument).toList();
	}

	private static Map<Instrument, Candidate> byInstrument(Basket basket) {
		Map<Instrument, Candidate> byInstrument = new IdentityHashMap<>();
		for (Candidate candidate : basket.candidates()) {
			byInstrument.put(candidate.instrument(), candidate);
		}
		return byInstrument;
	}

	private static RankedEntry entry(Candidate candidate, Simulation simulation, BigDecimal realReturn) {
		return new RankedEntry(candidate.id(), candidate.kind(), candidate.name(), candidate.entity(),
				SourceRegistry.of(candidate.sourceId()), candidate.confidence(), candidate.verifiedAt(),
				candidate.captureDate(), candidate.dataDate(), candidate.rateUsed(), simulation.finalAmount(),
				simulation.earned(), simulation.nominalReturn(), realReturn, simulation.conditional().orElse(null),
				candidate.warnings());
	}

	private static IneligibleEntry ineligible(Candidate candidate, Ineligible ineligible) {
		return new IneligibleEntry(candidate.id(), candidate.kind(), candidate.name(),
				SourceRegistry.of(candidate.sourceId()), candidate.captureDate(), candidate.dataDate(), ineligible);
	}

	private record Basket(List<Candidate> candidates, List<ExcludedEntry> excluded) {
	}

	private record Candidate(Instrument instrument, RankedKind kind, String id, String name, String entity,
			String sourceId, Confidence confidence, LocalDate verifiedAt, LocalDate captureDate, LocalDate dataDate,
			RateUsed rateUsed, List<ValidationFlag> warnings) {
	}
}
