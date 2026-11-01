package ar.rindetuplata.api.ranking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The conditional-GET contract of the two public read endpoints (T3a, L22): a weak {@code ETag} built from the data
 * version and the normalized request, {@code Cache-Control: public, no-cache} (a shared cache may store the answer
 * but must revalidate it, which is a 304 for the price of one version query), and a {@code 304} for a matching
 * {@code If-None-Match}. The tag is a digest, so nothing about the request or the database leaks through it, and it
 * is weak because the same data and query may serialize to different bytes across releases.
 *
 * <p>Only entity tags are matched. A wildcard {@code If-None-Match: *} is not a match: "any current representation
 * exists" cannot be decided without doing the work this class exists to skip, and a browser never sends it on a GET.
 */
final class ConditionalResponses {

	static final String CACHE_CONTROL = "public, no-cache";

	static final String DESCRIPTION = "Cacheable by validation. The response carries a weak ETag derived from the "
			+ "version of the stored data and the normalized request, and Cache-Control: public, no-cache. Send "
			+ "the ETag back in If-None-Match to get a 304 with no body while the data and the request are the "
			+ "same; a new ingestion run or a curated-rate change produces a new ETag.";

	static final String ETAG_DESCRIPTION = "Weak entity tag of the data version and the request.";

	static final String CACHE_DESCRIPTION = "public, no-cache: store, but revalidate with If-None-Match.";

	static final String NOT_MODIFIED_DESCRIPTION = "If-None-Match matched the current ETag; the body is empty.";

	private static final String WEAK_PREFIX = "W/";

	private static final int DIGEST_BYTES = 16;

	private ConditionalResponses() {
	}

	/** {@code W/"<22 url-safe characters>"}: 128 bits of SHA-256 over the parts. */
	static String etag(String dataVersion, String request) {
		MessageDigest digest = sha256();
		digest.update(dataVersion.getBytes(StandardCharsets.UTF_8));
		digest.update((byte) 0);
		digest.update(request.getBytes(StandardCharsets.UTF_8));
		byte[] head = java.util.Arrays.copyOf(digest.digest(), DIGEST_BYTES);
		return WEAK_PREFIX + '"' + Base64.getUrlEncoder().withoutPadding().encodeToString(head) + '"';
	}

	/** Weak comparison (RFC 9110, 8.8.3.2): the opaque tags are equal whether either side carries {@code W/}. */
	static boolean matches(List<String> ifNoneMatch, String etag) {
		String opaque = opaque(etag);
		return ifNoneMatch.stream().map(ConditionalResponses::opaque).anyMatch(opaque::equals);
	}

	static <T> ResponseEntity<T> ok(String etag, T body) {
		return ResponseEntity.ok().header(HttpHeaders.ETAG, etag).header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
				.body(body);
	}

	static <T> ResponseEntity<T> notModified(String etag) {
		return ResponseEntity.status(HttpStatus.NOT_MODIFIED).header(HttpHeaders.ETAG, etag)
				.header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL).build();
	}

	private static String opaque(String tag) {
		String trimmed = tag.strip();
		return trimmed.startsWith(WEAK_PREFIX) ? trimmed.substring(WEAK_PREFIX.length()) : trimmed;
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("every JVM provides SHA-256", e);
		}
	}
}
