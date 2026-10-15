package ar.rindetuplata.account;

/** The JSON request body of {@code POST /api/v1/auth/login}; the form variant reads the same {@code token}. */
public record LoginRequest(String token) {
}
