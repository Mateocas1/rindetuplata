package ar.rindetuplata.apikey;

import java.time.Instant;

/**
 * The answer of the one request that ever shows a key (T6d): the same fields as {@link ApiKeyResponse} plus the
 * plaintext {@code key}, which is not stored anywhere and cannot be read again.
 */
public record CreatedApiKeyResponse(long id, String name, String prefix, String plan, Instant createdAt, String key) {
}
