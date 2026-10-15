package ar.rindetuplata.curation;

import java.math.BigDecimal;
import java.util.List;

/**
 * The body of {@code PUT /api/v1/admin/wallet-rates/{walletId}} (T6b, S12): the kept document shape of the retired
 * classpath seed, with the TNA in percent and the floor/ceiling in ARS. The path already names the wallet, so the
 * body only carries what the admin edits.
 */
public record WalletRateRequest(String name, List<TierRequest> tiers, List<ConditionRequest> conditions, String note) {
}
