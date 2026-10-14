package ar.rindetuplata.ranking;

import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.ValidationFlag;
import ar.rindetuplata.source.plazofijo.TermRate;
import ar.rindetuplata.source.wallet.WalletCondition;
import ar.rindetuplata.source.wallet.WalletTier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the {@code jsonb} columns back into the T3 record shapes: {@code details} (tiers, conditions, terms,
 * currency) and {@code flags}. The store writes them; the ranking must read them without reaching into
 * {@code store} internals.
 */
final class RankingJson {

	private static final JsonMapper MAPPER = JsonMapper.builder()
			.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
			.build();

	private RankingJson() {
	}

	static JsonNode read(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return MAPPER.readTree(json);
		} catch (JacksonException e) {
			throw new IllegalStateException("Cannot read stored JSON: " + json, e);
		}
	}

	static List<ValidationFlag> flags(String json) {
		JsonNode node = read(json);
		if (node == null || !node.isArray()) {
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

	static List<WalletTier> walletTiers(JsonNode details) {
		return tiers(field(details, "tiers"));
	}

	static List<WalletCondition> walletConditions(JsonNode details) {
		JsonNode nodes = field(details, "conditions");
		if (nodes == null || !nodes.isArray()) {
			return List.of();
		}
		List<WalletCondition> conditions = new ArrayList<>();
		for (JsonNode node : nodes) {
			conditions.add(new WalletCondition(string(node, "condition"), tiers(node.path("tiers"))));
		}
		return List.copyOf(conditions);
	}

	static List<TermRate> terms(JsonNode details) {
		JsonNode nodes = field(details, "terms");
		if (nodes == null || !nodes.isArray()) {
			return List.of();
		}
		List<TermRate> terms = new ArrayList<>();
		for (JsonNode node : nodes) {
			terms.add(new TermRate(integer(node.path("minDays")), integer(node.path("maxDays")),
					decimal(node.path("minAmount")), decimal(node.path("maxAmount")), decimal(node.path("tna"))));
		}
		return List.copyOf(terms);
	}

	static String text(JsonNode details, String fieldName) {
		if (details == null) {
			return null;
		}
		return string(details, fieldName);
	}

	private static List<WalletTier> tiers(JsonNode nodes) {
		if (nodes == null || !nodes.isArray()) {
			return List.of();
		}
		List<WalletTier> tiers = new ArrayList<>();
		for (JsonNode node : nodes) {
			tiers.add(new WalletTier(decimal(node.path("floor")), decimal(node.path("ceiling")),
					decimal(node.path("tna"))));
		}
		return List.copyOf(tiers);
	}

	private static JsonNode field(JsonNode details, String name) {
		if (details == null) {
			return null;
		}
		JsonNode node = details.path(name);
		return node == null || node.isMissingNode() || node.isNull() ? null : node;
	}

	private static String string(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isString() ? value.stringValue() : null;
	}

	private static BigDecimal decimal(JsonNode node) {
		return node == null || node.isMissingNode() || node.isNull() ? null : node.decimalValue();
	}

	private static Integer integer(JsonNode node) {
		BigDecimal value = decimal(node);
		return value == null ? null : value.intValueExact();
	}
}
