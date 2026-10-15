package ar.rindetuplata.curation;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin curation panel API of T6b (S12). Every path is under {@code /api/v1/admin/**}, so Spring Security
 * requires {@code ROLE_ADMIN}; the actor of every write is the authenticated admin email. Errors are the shared
 * RFC 9457 {@code ProblemDetail} bodies that name the offending field.
 */
@RestController
@RequestMapping("/api/v1/admin/wallet-rates")
public class CurationController {

	private final CuratedWalletRateService walletRates;

	public CurationController(CuratedWalletRateService walletRates) {
		this.walletRates = walletRates;
	}

	/** Every curated rate with its {@code verifiedAt} and how many days ago that was. */
	@GetMapping
	List<WalletRateView> list() {
		return walletRates.list();
	}

	/** Creates or replaces one curated rate, verified today and attributed to the admin. */
	@PutMapping("/{walletId}")
	WalletRateView put(@PathVariable String walletId, @RequestBody WalletRateRequest request,
			Authentication authentication) {
		return walletRates.save(walletId, request, authentication.getName());
	}

	/** Re-verifies today's rate of one wallet without changing its tiers. */
	@PostMapping("/{walletId}/confirm")
	WalletRateView confirm(@PathVariable String walletId, Authentication authentication) {
		return walletRates.confirm(walletId, authentication.getName());
	}

	/** The audit trail of one wallet, newest first. */
	@GetMapping("/{walletId}/audit")
	List<AuditEntryView> audit(@PathVariable String walletId) {
		return walletRates.audit(walletId);
	}
}
