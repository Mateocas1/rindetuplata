package ar.rindetuplata.alert;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The recorded alert payloads of {@code src/test/resources/fixtures/alert/} (T6c): the shape an outbox row held
 * before the alert types (L32 a) and the payloads the new types write. A test that delivers one more than once
 * gives it a fresh event id.
 */
final class AlertFixtures {

	static final String STORED_ALERT_TRIGGERED = "alert-triggered-stored.json";
	static final String BEST_CHANGED = "best-changed.json";
	static final String RATE_DROP = "rate-drop.json";
	static final String WEEKLY_DIGEST = "weekly-digest.json";

	private AlertFixtures() {
	}

	static String read(String name) {
		try (var in = AlertFixtures.class.getResourceAsStream("/fixtures/alert/" + name)) {
			if (in == null) {
				throw new IllegalArgumentException("No alert fixture " + name);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** The payload of a fixture with its event id replaced, so the same shape can be delivered as a new event. */
	static String payload(String name, UUID eventId) {
		return read(name).replaceFirst("\"eventId\":\"[0-9a-f-]{36}\"", "\"eventId\":\"" + eventId + "\"");
	}

}
