package ar.rindetuplata.source;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Parsing helpers shared by the adapters. JSON numbers are read as {@link BigDecimal}, never as double. */
public final class JsonSupport {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private JsonSupport() {
    }

    /** Parses a body that must be a JSON object or array. */
    public static JsonNode readTree(SourceRef source, byte[] body) {
        try {
            JsonNode root = MAPPER.readTree(body);
            if (root == null || !root.isContainer()) {
                throw new SourceException(source, "body is not a JSON object or array", null);
            }
            return root;
        } catch (JacksonException e) {
            throw new SourceException(source, "malformed JSON body", e);
        }
    }

    /** The array at {@code field} of an object, or the root itself when {@code field} is null. */
    public static JsonNode array(SourceRef source, JsonNode node, String field) {
        JsonNode array = field == null ? node : node.path(field);
        if (!array.isArray()) {
            throw new SourceException(source, "expected a JSON array" + (field == null ? "" : " at '" + field + "'"), null);
        }
        return array;
    }

    /** A number as BigDecimal; {@code null} when absent or JSON null. */
    public static BigDecimal decimal(SourceRef source, JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            throw new SourceException(source, "field '" + field + "' is not a number", null);
        }
        return value.decimalValue();
    }

    public static Integer integer(SourceRef source, JsonNode node, String field) {
        BigDecimal value = decimal(source, node, field);
        if (value == null) {
            return null;
        }
        try {
            return value.intValueExact();
        } catch (ArithmeticException e) {
            throw new SourceException(source, "field '" + field + "' is not an integer", e);
        }
    }

    public static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() ? value.stringValue() : null;
    }

    public static LocalDate date(SourceRef source, JsonNode node, String field) {
        String text = text(node, field);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new SourceException(source, "field '" + field + "' is not a date: " + text, e);
        }
    }
}
