package ar.rindetuplata.api.instrument;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Decodes the stored {@code jsonb} flag arrays into the view the API publishes. */
final class FlagJson {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private FlagJson() {
	}

	static List<ValidationFlagView> read(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			JsonNode node = MAPPER.readTree(json);
			if (!node.isArray()) {
				return List.of();
			}
			List<ValidationFlagView> flags = new ArrayList<>();
			for (JsonNode flag : node) {
				flags.add(new ValidationFlagView(
						flag.path("code").stringValue(),
						flag.path("severity").stringValue(),
						flag.path("message").stringValue()));
			}
			return List.copyOf(flags);
		}
		catch (JacksonException e) {
			throw new IllegalStateException("Cannot read stored validation flags: " + json, e);
		}
	}
}
