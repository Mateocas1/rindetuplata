package ar.rindetuplata.apikey;

import java.time.Instant;

/** One active API key as the list shows it (T6d): never the key itself nor its hash, only the display prefix. */
public record ApiKeyResponse(long id, String name, String prefix, String plan, Instant createdAt) {
}
