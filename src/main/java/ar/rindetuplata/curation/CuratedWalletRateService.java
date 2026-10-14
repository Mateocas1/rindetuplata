package ar.rindetuplata.curation;

import ar.rindetuplata.api.ApiException;
import ar.rindetuplata.source.ValidationRules;
import ar.rindetuplata.source.wallet.CuratedWalletRateAdapter;
import ar.rindetuplata.source.wallet.WalletRate;
import ar.rindetuplata.store.WalletRateWriter;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The curation write path of T6b (S12). A save or a confirm validates the document, stamps the verification day
 * ("today" in America/Argentina/Buenos_Aires) and the admin email, writes an audit entry and, in the same
 * transaction, publishes today's wallet snapshot through the T4 {@link WalletRateWriter}, so the ranking shows the
 * rate without waiting for the next ingestion.
 */
@Service
public class CuratedWalletRateService {

	static final String CREATED = "CREATED";

	static final String REPLACED = "REPLACED";

	static final String CONFIRMED = "CONFIRMED";

	private final CuratedWalletRateRepository repository;

	private final WalletRateWriter walletRates;

	private final Clock clock;

	public CuratedWalletRateService(CuratedWalletRateRepository repository, WalletRateWriter walletRates, Clock clock) {
		this.repository = repository;
		this.walletRates = walletRates;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<WalletRateView> list() {
		LocalDate today = today();
		return repository.findAll().stream().map(rate -> WalletRateView.of(rate, today)).toList();
	}

	@Transactional
	public WalletRateView save(String walletId, WalletRateRequest request, String actor) {
		WalletRateValidator.walletId(walletId);
		WalletRateValidator.document(request);
		LocalDate today = today();
		boolean existing = repository.find(walletId).isPresent();
		CuratedWalletRate rate = new CuratedWalletRate(walletId, request.name().trim(),
				CurationJson.node(request.tiers()),
				CurationJson.node(request.conditions() == null ? List.of() : request.conditions()), request.note(),
				today, now(), actor);
		repository.upsert(rate);
		repository.record(walletId, existing ? REPLACED : CREATED, CuratedWalletRateRepository.payload(rate), actor,
				now());
		publish(rate);
		return WalletRateView.of(rate, today);
	}

	@Transactional
	public WalletRateView confirm(String walletId, String actor) {
		WalletRateValidator.walletId(walletId);
		CuratedWalletRate existing = repository.find(walletId).orElseThrow(() -> unknown(walletId));
		LocalDate today = today();
		CuratedWalletRate rate = new CuratedWalletRate(existing.walletId(), existing.name(), existing.tiers(),
				existing.conditions(), existing.note(), today, now(), actor);
		repository.upsert(rate);
		repository.record(walletId, CONFIRMED, CuratedWalletRateRepository.payload(rate), actor, now());
		publish(rate);
		return WalletRateView.of(rate, today);
	}

	@Transactional(readOnly = true)
	public List<AuditEntryView> audit(String walletId) {
		WalletRateValidator.walletId(walletId);
		if (repository.find(walletId).isEmpty()) {
			throw unknown(walletId);
		}
		return repository.audit(walletId);
	}

	/** Today's wallet snapshot, through the same writer the ingestion uses, so the ranking picks it up. */
	private void publish(CuratedWalletRate rate) {
		WalletRate published = CuratedWalletRateAdapter.toWalletRate(rate.walletId(), rate.name(), rate.tiers(),
				rate.conditions(), rate.verifiedAt(), clock.instant());
		walletRates.write(List.of(published), rate.verifiedAt());
	}

	private LocalDate today() {
		return ValidationRules.localDate(clock.instant());
	}

	private OffsetDateTime now() {
		return OffsetDateTime.ofInstant(clock.instant(), ValidationRules.ARGENTINA);
	}

	private static ApiException unknown(String walletId) {
		return ApiException.notFound("walletId", "Unknown wallet id '" + walletId + "'");
	}
}
