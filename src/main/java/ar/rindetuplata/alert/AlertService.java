package ar.rindetuplata.alert;

import ar.rindetuplata.api.ApiException;
import ar.rindetuplata.ranking.RankedKind;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The alert-rules API of T6c (S3, S13). A rule belongs to the authenticated email; the threshold arrives as a
 * percent and is stored as the fraction the evaluation compares against. Values the caller got wrong are 400s
 * naming the field ({@code ApiException}), an unknown rule of the caller's is a 404, and the per-user cap of active
 * rules is refused with the cap in the detail. The create runs in one transaction that first locks the user's
 * {@code app_user} row, so concurrent creates of one user are serialized and the cap holds exactly.
 */
@Service
public class AlertService {

	/** The most active rules one user may hold: beyond it the UI could never explain the alerts. */
	public static final int MAX_ACTIVE_RULES = 20;

	private static final BigDecimal MAX_THRESHOLD_PERCENT = new BigDecimal("300");

	private final AlertRuleRepository rules;

	private final Clock clock;

	public AlertService(AlertRuleRepository rules, Clock clock) {
		this.rules = rules;
		this.clock = clock;
	}

	public List<AlertResponse> list(String email) {
		return rules.findByEmail(email).stream().map(AlertResponse::of).toList();
	}

	@Transactional
	public AlertResponse create(String email, AlertRequest request) {
		RankedKind kind = kind(request);
		BigDecimal threshold = threshold(request);
		if (request.instrumentId() != null && !rules.instrumentExists(request.instrumentId())) {
			throw ApiException.badRequest("instrumentId", "Instrument " + request.instrumentId() + " is unknown");
		}
		rules.lockUser(email);
		if (rules.countActive(email) >= MAX_ACTIVE_RULES) {
			throw ApiException.badRequest(null,
					"At most " + MAX_ACTIVE_RULES + " active alert rules per user; delete one first");
		}
		return AlertResponse.of(rules.insert(email, kind, request.instrumentId(), threshold, clock.instant()));
	}

	public void delete(String email, long id) {
		if (rules.delete(email, id) == 0) {
			throw ApiException.notFound("id", "Alert rule " + id + " is unknown");
		}
	}

	private static RankedKind kind(AlertRequest request) {
		if (request.kind() == null || request.kind().isBlank()) {
			return null;
		}
		try {
			return RankedKind.valueOf(request.kind().trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			throw ApiException.badRequest("kind", "kind must be one of PLAZO_FIJO, WALLET, FUND, CRYPTO (got '"
					+ request.kind() + "')");
		}
	}

	/** The percent as the fraction stored: 25 -> 0.25, exactly, without rounding. */
	private static BigDecimal threshold(AlertRequest request) {
		BigDecimal percent = request.thresholdPercent();
		if (percent == null) {
			throw ApiException.badRequest("thresholdPercent", "thresholdPercent is required");
		}
		if (percent.signum() <= 0 || percent.compareTo(MAX_THRESHOLD_PERCENT) > 0) {
			throw ApiException.badRequest("thresholdPercent", "thresholdPercent must be greater than 0 and at most "
					+ MAX_THRESHOLD_PERCENT.toPlainString() + " (got " + percent.toPlainString() + ")");
		}
		return percent.movePointLeft(2);
	}

}
