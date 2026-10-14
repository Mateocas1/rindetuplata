package ar.rindetuplata.curation;

import java.time.OffsetDateTime;
import tools.jackson.databind.JsonNode;

/**
 * One audit entry of a curated wallet rate (T6b, S12): what was written, by whom and when. {@code payload} is the
 * document as stored, so the panel can show exactly what a past action left behind.
 */
public record AuditEntryView(long id, String walletId, String action, JsonNode payload, String actor,
		OffsetDateTime at) {
}
