package ar.rindetuplata.apikey;

/**
 * What a valid API key is worth to the rate limit: the key's id and its plan. It carries no user and is never put
 * in the security context: a key identifies a quota, not a person.
 */
public record ApiKeyPrincipal(long id, ApiKeyPlan plan) {
}
