package ar.rindetuplata.curation;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** A labeled alternative of a stored wallet rate, with its own tiers. */
public record ConditionView(String condition, List<TierView> tiers) {

	static List<ConditionView> list(JsonNode nodes) {
		List<ConditionView> conditions = new ArrayList<>();
		if (nodes == null || !nodes.isArray()) {
			return conditions;
		}
		for (JsonNode node : nodes) {
			conditions.add(new ConditionView(CurationJson.text(node, "condition"), TierView.list(node.path("tiers"))));
		}
		return conditions;
	}
}
