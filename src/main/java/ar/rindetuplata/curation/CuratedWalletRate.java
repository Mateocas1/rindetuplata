package ar.rindetuplata.curation;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import tools.jackson.databind.JsonNode;

/**
 * One row of {@code curated_wallet_rate} (T6b, S12): the wallet document exactly as stored, with TNA in percent.
 * {@code updatedBy} is the admin email of the last write; {@code verifiedAt} is the day it was verified.
 */
public record CuratedWalletRate(
		String walletId,
		String name,
		JsonNode tiers,
		JsonNode conditions,
		String note,
		LocalDate verifiedAt,
		OffsetDateTime updatedAt,
		String updatedBy) {
}
