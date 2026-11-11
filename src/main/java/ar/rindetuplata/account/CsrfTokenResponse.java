package ar.rindetuplata.account;

/**
 * The body of {@code GET /api/v1/auth/csrf}: the header the SPA sends and the raw token to put in it. The
 * same request also writes the {@code XSRF-TOKEN} cookie, but the browser never reads it.
 */
public record CsrfTokenResponse(String headerName, String token) {
}
