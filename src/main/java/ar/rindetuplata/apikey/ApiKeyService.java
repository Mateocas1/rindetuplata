package ar.rindetuplata.apikey;

import ar.rindetuplata.api.ApiException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API keys of T6d (S6, L32b). Creating runs in one transaction that first locks the user's {@code app_user} row,
 * so concurrent creates of one user are serialized and the cap of {@link #MAX_ACTIVE_KEYS} holds exactly. Only the
 * SHA-256 and a display prefix are stored; the plaintext goes back in the one create response. A key of somebody
 * else, an unknown one and an already revoked one are all the same 404 on revoke. The same class resolves a
 * presented key for the rate limit, always against the database, so revocation is effective at once.
 */
@Service
public class ApiKeyService implements ApiKeyAuthenticator {

	/** The most active keys one user may hold. */
	public static final int MAX_ACTIVE_KEYS = 3;

	static final int MAX_NAME_LENGTH = 60;

	private final ApiKeyRepository keys;

	private final Clock clock;

	public ApiKeyService(ApiKeyRepository keys, Clock clock) {
		this.keys = keys;
		this.clock = clock;
	}

	@Transactional
	public CreatedApiKeyResponse create(String email, CreateApiKeyRequest request) {
		String name = name(request);
		keys.lockUser(email);
		if (keys.countActive(email) >= MAX_ACTIVE_KEYS) {
			throw ApiException.badRequest(null,
					"At most " + MAX_ACTIVE_KEYS + " active API keys per user; revoke one first");
		}
		String key = ApiKeys.generate();
		ApiKeyRepository.StoredKey stored = keys.insert(email, name, ApiKeys.displayPrefix(key), ApiKeys.hash(key),
				clock.instant());
		return new CreatedApiKeyResponse(stored.id(), stored.name(), stored.prefix(), stored.plan().name(),
				stored.createdAt(), key);
	}

	public List<ApiKeyResponse> list(String email) {
		return keys.findActive(email).stream()
				.map(key -> new ApiKeyResponse(key.id(), key.name(), key.prefix(), key.plan().name(), key.createdAt()))
				.toList();
	}

	public void revoke(String email, long id) {
		if (keys.revoke(email, id, clock.instant()) == 0) {
			throw ApiException.notFound("id", "API key " + id + " is unknown");
		}
	}

	@Override
	public Optional<ApiKeyPrincipal> authenticate(String presentedKey) {
		if (!ApiKeys.looksLikeKey(presentedKey)) {
			return Optional.empty();
		}
		return keys.findActiveByHash(ApiKeys.hash(presentedKey));
	}

	private static String name(CreateApiKeyRequest request) {
		if (request == null || request.name() == null || request.name().isBlank()) {
			return null;
		}
		String name = request.name().strip();
		if (name.length() > MAX_NAME_LENGTH) {
			throw ApiException.badRequest("name", "name must be at most " + MAX_NAME_LENGTH + " characters (got "
					+ name.length() + ")");
		}
		return name;
	}
}
