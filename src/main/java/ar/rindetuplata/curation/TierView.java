package ar.rindetuplata.curation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** One balance band of a stored wallet rate: TNA in percent, floor and ceiling in ARS ({@code null} = no bound). */
public record TierView(BigDecimal floor, BigDecimal ceiling, BigDecimal tnaPercent) {

	static List<TierView> list(JsonNode nodes) {
		List<TierView> tiers = new ArrayList<>();
		if (nodes == null || !nodes.isArray()) {
			return tiers;
		}
		for (JsonNode node : nodes) {
			tiers.add(new TierView(CurationJson.decimal(node, "floor"), CurationJson.decimal(node, "ceiling"),
					CurationJson.decimal(node, "tnaPercent")));
		}
		return tiers;
	}
}
