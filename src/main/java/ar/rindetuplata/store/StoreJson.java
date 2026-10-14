package ar.rindetuplata.store;

import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.ValidationFlag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Encoding of the {@code jsonb} columns: validation flags and the kind-specific details of a rate. */
final class StoreJson {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();
	private static final String EMPTY_ARRAY = "[]";

	private StoreJson() {
	}

	static String write(Object value) {
		try {
			return MAPPER.writeValueAsString(value);
		} catch (JacksonException e) {
			throw new IllegalStateException("Cannot encode value as JSON", e);
		}
	}

	/** Flags as given by the validation layer, so a reader gets exactly what the source layer produced. */
	static String flags(List<ValidationFlag> flags) {
		if (flags == null || flags.isEmpty()) {
			return EMPTY_ARRAY;
		}
		List<Map<String, Object>> encoded = new ArrayList<>(flags.size());
		for (ValidationFlag flag : flags) {
			Map<String, Object> node = new LinkedHashMap<>();
			node.put("code", flag.code().name());
			node.put("severity", flag.severity().name());
			node.put("message", flag.message());
			encoded.add(node);
		}
		return write(encoded);
	}

	static List<ValidationFlag> readFlags(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		JsonNode node = read(json);
		if (!node.isArray()) {
			return List.of();
		}
		List<ValidationFlag> flags = new ArrayList<>();
		for (JsonNode flag : node) {
			flags.add(new ValidationFlag(
					FlagCode.valueOf(flag.path("code").stringValue()),
					Severity.valueOf(flag.path("severity").stringValue()),
					flag.path("message").stringValue()));
		}
		return List.copyOf(flags);
	}

	static JsonNode read(String json) {
		try {
			return MAPPER.readTree(json);
		} catch (JacksonException e) {
			throw new IllegalStateException("Cannot read stored JSON: " + json, e);
		}
	}
}
