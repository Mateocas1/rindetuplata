package ar.rindetuplata.apikey;

/** The optional body of {@code POST /api/v1/account/api-keys}: a label of at most 60 characters. */
public record CreateApiKeyRequest(String name) {
}
