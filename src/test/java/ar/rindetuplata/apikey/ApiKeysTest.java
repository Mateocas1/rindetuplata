package ar.rindetuplata.apikey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** T6d (S6, L32b): the shape of a key, the display prefix and the one-way hash that is all the database keeps. */
class ApiKeysTest {

	@Test
	void aKeyIsRtpPlus32RandomBytesInBase64UrlWithoutPadding() {
		String key = ApiKeys.generate();

		assertThat(key).matches("rtp_[A-Za-z0-9_-]{43}");
	}

	@Test
	void keysDoNotRepeat() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 200; i++) {
			assertThat(seen.add(ApiKeys.generate())).isTrue();
		}
	}

	@Test
	void theDisplayPrefixIsTheFirstTenCharactersAndRevealsLittle() {
		String key = "rtp_AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE";

		assertThat(ApiKeys.displayPrefix(key)).isEqualTo("rtp_AbCdEf");
	}

	@Test
	void theHashIsTheLowerCaseHexSha256OfTheKey() {
		// The FIPS 180-2 test vector for "abc".
		assertThat(ApiKeys.hash("abc"))
				.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
		assertThat(ApiKeys.hash("rtp_x")).hasSize(64).matches("[0-9a-f]{64}");
	}

	@Test
	void onlyTheExactShapeLooksLikeAKeySoGarbageNeverReachesTheDatabase() {
		String key = ApiKeys.generate();

		assertThat(ApiKeys.looksLikeKey(key)).isTrue();
		assertThat(ApiKeys.looksLikeKey(key + "a")).isFalse();
		assertThat(ApiKeys.looksLikeKey(key.substring(1))).isFalse();
		assertThat(ApiKeys.looksLikeKey("xtp_" + key.substring(4))).isFalse();
		assertThat(ApiKeys.looksLikeKey("rtp_" + "!".repeat(43))).isFalse();
		assertThat(ApiKeys.looksLikeKey("")).isFalse();
		assertThat(ApiKeys.looksLikeKey(null)).isFalse();
	}
}
