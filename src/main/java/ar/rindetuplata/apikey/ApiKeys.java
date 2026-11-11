package ar.rindetuplata.apikey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * The shape of an API key: {@code rtp_} plus 32 random bytes in unpadded base64url (47 characters).
 * The database keeps only the lower-case hex SHA-256 of the whole key and its first ten characters for display. A key
 * has 256 bits of entropy, so a plain unsalted SHA-256 is enough: there is nothing to guess and nothing to brute force.
 */
final class ApiKeys {

	static final String PREFIX = "rtp_";

	private static final int RANDOM_BYTES = 32;

	private static final int DISPLAY_PREFIX_LENGTH = 10;

	private static final Pattern SHAPE = Pattern.compile("rtp_[A-Za-z0-9_-]{43}");

	private static final SecureRandom RANDOM = new SecureRandom();

	private ApiKeys() {
	}

	static String generate() {
		byte[] bytes = new byte[RANDOM_BYTES];
		RANDOM.nextBytes(bytes);
		return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** What the list shows so the owner can tell keys apart: {@code rtp_} and the first six characters. */
	static String displayPrefix(String key) {
		return key.substring(0, DISPLAY_PREFIX_LENGTH);
	}

	static String hash(String key) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required by every Java platform", e);
		}
	}

	/** True only for the exact shape {@link #generate()} produces, so garbage never costs a database read. */
	static boolean looksLikeKey(String presented) {
		return presented != null && SHAPE.matcher(presented).matches();
	}
}
