package ar.rindetuplata.account;

/** The request body of {@code POST /api/v1/auth/magic-link}. */
public record MagicLinkRequest(String email) {
}
