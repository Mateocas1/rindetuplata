package ar.rindetuplata.account;

import java.time.Instant;

/** One row of {@code app_user}: created on the first successful login, never by a magic-link request (S13). */
public record AppUser(long id, String email, Instant createdAt, Instant lastLoginAt) {
}
