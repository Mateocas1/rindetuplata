package ar.rindetuplata.curation;

import ar.rindetuplata.api.ApiException;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validation of the admin wallet-rate API (T6b, S12). Every rejected value becomes a 400 {@code ProblemDetail} whose
 * {@code parameter} names the field to fix: the id pattern, a name, at least one tier, the TNA percent range, a
 * non-negative floor, a ceiling above its floor, and tiers that are ordered and do not overlap.
 */
public final class WalletRateValidator {

	/** Wallet ids of the panel and of the instrument natural keys: lowercase letters, digits and dashes. */
	public static final Pattern WALLET_ID = Pattern.compile("[a-z0-9-]{2,40}");

	public static final BigDecimal MAX_TNA_PERCENT = new BigDecimal("300");

	private WalletRateValidator() {
	}

	/** The id in the path, checked before it reaches the table. */
	public static String walletId(String walletId) {
		if (walletId == null || !WALLET_ID.matcher(walletId).matches()) {
			throw ApiException.badRequest("walletId",
					"Wallet id '" + walletId + "' must match " + WALLET_ID.pattern());
		}
		return walletId;
	}

	/** The body of a PUT, checked field by field before anything is stored. */
	public static void document(WalletRateRequest request) {
		if (request == null || request.name() == null || request.name().isBlank()) {
			throw ApiException.badRequest("name", "A wallet name is required");
		}
		tiers("tiers", request.tiers());
		if (request.conditions() != null) {
			for (int i = 0; i < request.conditions().size(); i++) {
				ConditionRequest condition = request.conditions().get(i);
				String prefix = "conditions[" + i + "]";
				if (condition == null || condition.condition() == null || condition.condition().isBlank()) {
					throw ApiException.badRequest(prefix + ".condition", "A condition label is required");
				}
				tiers(prefix + ".tiers", condition.tiers());
			}
		}
	}

	/**
	 * One schedule: at least one tier, each with a TNA percent above 0 and at most 300, a floor of 0 or more when
	 * given and, when bounded, a ceiling above its floor. Tiers must ascend without overlapping, so every floor from
	 * the second tier on must reach the previous ceiling (an unbounded previous tier leaves no room).
	 */
	private static void tiers(String field, List<TierRequest> tiers) {
		if (tiers == null || tiers.isEmpty()) {
			throw ApiException.badRequest(field, "At least one rate tier is required");
		}
		BigDecimal previousCeiling = null;
		for (int i = 0; i < tiers.size(); i++) {
			TierRequest tier = tiers.get(i);
			String prefix = field + "[" + i + "]";
			if (tier == null) {
				throw ApiException.badRequest(prefix, "A rate tier is required");
			}
			BigDecimal floor = tier.floor() == null ? BigDecimal.ZERO : tier.floor();
			BigDecimal ceiling = tier.ceiling();
			BigDecimal tnaPercent = tier.tnaPercent();
			if (floor.signum() < 0) {
				throw ApiException.badRequest(prefix + ".floor", "The floor cannot be negative");
			}
			if (tnaPercent == null || tnaPercent.signum() <= 0 || tnaPercent.compareTo(MAX_TNA_PERCENT) > 0) {
				throw ApiException.badRequest(prefix + ".tnaPercent",
						"The TNA percent must be greater than 0 and at most " + MAX_TNA_PERCENT);
			}
			if (ceiling != null && ceiling.compareTo(floor) <= 0) {
				throw ApiException.badRequest(prefix + ".ceiling", "The ceiling must be greater than the floor");
			}
			if (i > 0 && (previousCeiling == null || floor.compareTo(previousCeiling) < 0)) {
				throw ApiException.badRequest(field, "Tiers must be ordered and must not overlap");
			}
			previousCeiling = ceiling;
		}
	}
}
