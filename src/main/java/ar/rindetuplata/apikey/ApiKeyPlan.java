package ar.rindetuplata.apikey;

/**
 * The plan of an API key (T6d, S6): every key a user creates is {@code FREE}; {@code PARTNER} is set by the operator
 * in SQL ({@code docs/deploy.md}). The limits of each plan are configuration ({@code rindetuplata.api.plans.*}); a
 * caller without a key is the anonymous plan of {@code rindetuplata.api.rate-limit.*}.
 */
public enum ApiKeyPlan {
	FREE, PARTNER
}
