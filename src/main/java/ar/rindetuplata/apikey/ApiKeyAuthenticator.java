package ar.rindetuplata.apikey;

import java.util.Optional;

/** Resolves a presented key to its principal; empty when the key is unknown or revoked. */
@FunctionalInterface
public interface ApiKeyAuthenticator {

	Optional<ApiKeyPrincipal> authenticate(String presentedKey);
}
