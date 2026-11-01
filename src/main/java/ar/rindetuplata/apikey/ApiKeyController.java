package ar.rindetuplata.apikey;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The API-key endpoints of T6d (S6, L32b), for the signed-in user only: security requires a session on
 * {@code /api/v1/account/**} and a CSRF token on every state-changing request. A key sent in {@code X-Api-Key} never
 * reaches this class as an identity: it only selects the limits of the public read API.
 */
@RestController
@RequestMapping("/api/v1/account/api-keys")
public class ApiKeyController {

	private final ApiKeyService keys;

	public ApiKeyController(ApiKeyService keys) {
		this.keys = keys;
	}

	@GetMapping
	@Operation(summary = "List my API keys", description = "The active API keys of the signed-in user, oldest first: "
			+ "id, label, display prefix, plan and creation date. The key itself is never returned again.")
	List<ApiKeyResponse> list(Authentication authentication) {
		return keys.list(authentication.getName());
	}

	@PostMapping
	@Operation(summary = "Create an API key", description = "Creates a key for the signed-in user and returns it once, "
			+ "in the key field: only its SHA-256 and a display prefix are stored, so it cannot be shown again. A user "
			+ "may hold at most 3 active keys (a 400 beyond that). Send the key in the X-Api-Key header of the public "
			+ "read API to get the FREE plan limits (a bucket per key and a daily quota); a key never authenticates "
			+ "an account endpoint. The body is optional: {\"name\": label of at most 60 characters}.")
	ResponseEntity<CreatedApiKeyResponse> create(@RequestBody(required = false) CreateApiKeyRequest request,
			Authentication authentication) {
		return ResponseEntity.status(HttpStatus.CREATED).body(keys.create(authentication.getName(), request));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Revoke an API key", description = "Revokes one of the caller's active keys; it stops "
			+ "working on the next request. An unknown id, a key of somebody else and an already revoked key are "
			+ "the same 404.")
	ResponseEntity<Void> revoke(@PathVariable long id, Authentication authentication) {
		keys.revoke(authentication.getName(), id);
		return ResponseEntity.noContent().build();
	}
}
