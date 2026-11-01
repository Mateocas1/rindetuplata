package ar.rindetuplata.alert;

import ar.rindetuplata.api.ApiException;
import ar.rindetuplata.ranking.RankedKind;
import ar.rindetuplata.store.InstrumentKind;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The alert-rules API of T6c (S3, S13, S6 L32 a). A rule belongs to the authenticated email and has a type
 * (THRESHOLD when the request names none); each type accepts only its own fields. For a THRESHOLD rule the threshold
 * arrives as a percent and is stored as the fraction the evaluation compares against. Values the caller got wrong
 * (an unknown type or kind, a field the type does not take, a missing required one, an instrument of the wrong kind)
 * are 400s naming the field ({@code ApiException}), an unknown rule of the caller's is a 404, and the per-user cap of
 * active rules, whatever their type, is refused with the cap in the detail. The create runs in one transaction that
 * first locks the user's {@code app_user} row, so concurrent creates of one user are serialized and the cap holds
 * exactly.
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
		AlertType type = type(request);
		RankedKind kind = kind(request);
		BigDecimal threshold = null;
		switch (type) {
			case THRESHOLD -> {
				threshold = threshold(request);
				if (request.instrumentId() != null && !rules.instrumentExists(request.instrumentId())) {
					throw ApiException.badRequest("instrumentId", "Instrument " + request.instrumentId() + " is unknown");
				}
			}
			case BEST_CHANGED, WEEKLY_DIGEST -> {
				refuseThreshold(request, type);
				refuseInstrument(request, type);
			}
			case RATE_DROP -> kind = rateDropKind(request, kind);
		}
		rules.lockUser(email);
		if (rules.countActive(email) >= MAX_ACTIVE_RULES) {
			throw ApiException.badRequest(null,
					"At most " + MAX_ACTIVE_RULES + " active alert rules per user; delete one first");
		}
		return AlertResponse.of(rules.insert(email, type, kind, request.instrumentId(), threshold, clock.instant()));
	}

	public void delete(String email, long id) {
		if (rules.delete(email, id) == 0) {
			throw ApiException.notFound("id", "Alert rule " + id + " is unknown");
		}
	}

	/** The type the request names, THRESHOLD when it names none. */
	private static AlertType type(AlertRequest request) {
		if (request.type() == null || request.type().isBlank()) {
			return AlertType.THRESHOLD;
		}
		return AlertType.parse(request.type()).orElseThrow(() -> ApiException.badRequest("type",
				"type must be one of " + AlertType.NAMES + " (got '" + request.type() + "')"));
	}

	private static void refuseThreshold(AlertRequest request, AlertType type) {
		if (request.thresholdPercent() != null) {
			throw ApiException.badRequest("thresholdPercent", "thresholdPercent is not allowed for " + type + " rules");
		}
	}

	private static void refuseInstrument(AlertRequest request, AlertType type) {
		if (request.instrumentId() != null) {
			throw ApiException.badRequest("instrumentId", "instrumentId is not allowed for " + type + " rules");
		}
	}

	/**
	 * A RATE_DROP rule watches one plazo fijo or wallet, so the instrument is required and known, it is a bank's
	 * plazo fijo or a wallet, and the rule's kind is the instrument's: omitted, it is filled in; a different one is a
	 * 400. An FCI-backed wallet is a fund class in the store and is not accepted.
	 */
	private RankedKind rateDropKind(AlertRequest request, RankedKind requested) {
		Long instrumentId = request.instrumentId();
		if (instrumentId == null) {
			throw ApiException.badRequest("instrumentId", "instrumentId is required for RATE_DROP rules");
		}
		InstrumentKind stored = rules.instrumentKind(instrumentId)
			.orElseThrow(() -> ApiException.badRequest("instrumentId", "Instrument " + instrumentId + " is unknown"));
		RankedKind derived = switch (stored) {
			case PLAZO_FIJO_BANK -> RankedKind.PLAZO_FIJO;
			case WALLET -> RankedKind.WALLET;
			default -> throw ApiException.badRequest("instrumentId", "RATE_DROP needs an instrument that is a "
					+ "PLAZO_FIJO or WALLET (instrument " + instrumentId + " is " + stored + ")");
		};
		refuseThreshold(request, AlertType.RATE_DROP);
		if (requested != null && requested != derived) {
			throw ApiException.badRequest("kind", "kind must be " + derived + " (or omitted) for instrument "
					+ instrumentId + " (got '" + request.kind() + "')");
		}
		return derived;
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
