package ar.rindetuplata.curation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * One curated wallet rate as the admin API returns it (T6b, S12): the stored document plus the verification age the
 * panel shows. {@code daysSinceVerified} is measured against "today" in America/Argentina/Buenos_Aires, the day the
 * writes stamp.
 */
public record WalletRateView(
		String walletId,
		String name,
		List<TierView> tiers,
		List<ConditionView> conditions,
		String note,
		LocalDate verifiedAt,
		long daysSinceVerified,
		OffsetDateTime updatedAt,
		String updatedBy) {

	static WalletRateView of(CuratedWalletRate rate, LocalDate today) {
		return new WalletRateView(rate.walletId(), rate.name(), TierView.list(rate.tiers()),
				ConditionView.list(rate.conditions()), rate.note(), rate.verifiedAt(),
				ChronoUnit.DAYS.between(rate.verifiedAt(), today), rate.updatedAt(), rate.updatedBy());
	}
}
