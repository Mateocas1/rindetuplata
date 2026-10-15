package ar.rindetuplata.curation;

import java.math.BigDecimal;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** JSON of the curation layer: the percent document of V4 and the audit payloads (Jackson 3, tools.jackson). */
final class CurationJson {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private CurationJson() {
	}

	static String write(Object value) {
		return MAPPER.writeValueAsString(value);
	}

	static JsonNode read(String json) {
		return MAPPER.readTree(json);
	}

	/** The given value as a JSON tree; the request records serialize to the columns' document shape. */
	static JsonNode node(Object value) {
		return MAPPER.valueToTree(value);
	}

	/** A number at {@code field}, or {@code null} when it is absent or JSON null. */
	static BigDecimal decimal(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isNumber() ? value.decimalValue() : null;
	}

	/** A string at {@code field}, or {@code null} when it is absent or not a string. */
	static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isString() ? value.stringValue() : null;
	}
}
