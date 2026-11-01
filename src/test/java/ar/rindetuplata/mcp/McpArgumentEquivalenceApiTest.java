package ar.rindetuplata.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ar.rindetuplata.ranking.RankingQuery;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/**
 * T6e (S6, L32 c): the MCP tools accept exactly what {@code GET /api/v1/ranking} and {@code GET /api/v1/simulations}
 * accept and reject exactly what they reject, naming the same field. The tool validation mirrors the REST one (the
 * REST request classes are package-private), so this test is what keeps the two from drifting: each boundary comes
 * from the public {@link RankingQuery} constants, never from a literal, and every case is sent to both. It also
 * checks that the answers agree: the tools read the same services, so a ranked row has the same id and amounts.
 */
class McpArgumentEquivalenceApiTest extends McpIntegrationTest {

	/** One argument value as the query string spells it and as a JSON tool argument carries it. */
	private record Case(String name, String value, Object json) {
		static Case of(String name, Object value) {
			return new Case(name, value.toString(), value);
		}
	}

	private static List<Case> rankingCases() {
		List<Case> cases = new ArrayList<>();
		for (int days : new int[] { 0, 1, RankingQuery.MAX_DAYS, RankingQuery.MAX_DAYS + 1, -1 }) {
			cases.add(Case.of("days", days));
		}
		for (BigDecimal amount : new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ONE, RankingQuery.MAX_AMOUNT,
				RankingQuery.MAX_AMOUNT.add(BigDecimal.ONE), new BigDecimal("-1"), new BigDecimal("0.01") }) {
			cases.add(new Case("amount", amount.toPlainString(), amount));
		}
		for (int limit : new int[] { 0, 1, RankingQuery.MAX_LIMIT, RankingQuery.MAX_LIMIT + 1 }) {
			cases.add(Case.of("limit", limit));
		}
		cases.add(new Case("kind", "PLAZO_FIJO", List.of("PLAZO_FIJO")));
		cases.add(new Case("kind", "BOGUS", List.of("BOGUS")));
		cases.add(new Case("kind", "plazo_fijo", List.of("plazo_fijo")));
		cases.add(clientOf(RankingQuery.MAX_CLIENT_OF, false));
		cases.add(clientOf(RankingQuery.MAX_CLIENT_OF + 1, false));
		cases.add(clientOf(RankingQuery.MAX_CLIENT_OF + 1, true));
		cases.add(Case.of("asOf", "2026-10-14"));
		cases.add(Case.of("asOf", "2026-13-45"));
		cases.add(Case.of("asOf", "14/10/2026"));
		for (String asOf : new String[] { "+999999999-12-31", "-999999999-01-01", "0001-01-01", "9999-12-31" }) {
			cases.add(Case.of("asOf", asOf));
		}
		cases.add(Case.of("groupFunds", false));
		return cases;
	}

	/** {@code values} client ids, all different or all the same: the cap counts the list as sent. */
	private static Case clientOf(int values, boolean repeated) {
		List<Integer> ids = IntStream.rangeClosed(1, values).map(i -> repeated ? 1 : i).boxed().toList();
		return new Case("clientOf", ids.stream().map(String::valueOf).collect(Collectors.joining(",")), ids);
	}

	private static List<Case> simulationCases() {
		List<Case> cases = new ArrayList<>();
		for (int days : new int[] { 0, 1, RankingQuery.MAX_DAYS, RankingQuery.MAX_DAYS + 1 }) {
			cases.add(Case.of("days", days));
		}
		for (BigDecimal amount : new BigDecimal[] { BigDecimal.ZERO, RankingQuery.MAX_AMOUNT,
				RankingQuery.MAX_AMOUNT.add(BigDecimal.ONE) }) {
			cases.add(new Case("amount", amount.toPlainString(), amount));
		}
		cases.add(Case.of("client", true));
		return cases;
	}

	@Test
	void everyRankingBoundaryIsAcceptedOrRejectedLikeTheRestEndpointNamingTheSameField() throws Exception {
		try (McpSyncClient client = client()) {
			client.initialize();
			for (Case argument : rankingCases()) {
				MockHttpServletResponse rest = mockMvc
						.perform(get("/api/v1/ranking").param(argument.name(), argument.value())).andReturn()
						.getResponse();
				CallToolResult tool = call(client, "get_ranking", Map.of(argument.name(), argument.json()));

				assertSameVerdict("get_ranking " + argument, rest, tool);
			}
		}
	}

	@Test
	void moreClientBanksThanTheCapAreRefusedByBothAndTheCapItselfIsAccepted() throws Exception {
		try (McpSyncClient client = client()) {
			client.initialize();
			for (boolean repeated : new boolean[] { false, true }) {
				Case over = clientOf(RankingQuery.MAX_CLIENT_OF + 1, repeated);
				MockHttpServletResponse rest = mockMvc.perform(get("/api/v1/ranking").param("clientOf", over.value()))
						.andReturn().getResponse();
				CallToolResult tool = call(client, "get_ranking", Map.of("clientOf", over.json()));

				assertThat(rest.getStatus()).isEqualTo(400);
				assertThat(tool.isError()).isTrue();
				assertThat(structured(tool).get("error").asString()).isEqualTo("invalid_argument");
				assertThat(structured(tool).get("argument").asString()).isEqualTo("clientOf");
				assertThat(text(tool)).hasSizeLessThan(400);
			}
			Case atCap = clientOf(RankingQuery.MAX_CLIENT_OF, false);
			assertThat(mockMvc.perform(get("/api/v1/ranking").param("clientOf", atCap.value())).andReturn()
					.getResponse().getStatus()).isEqualTo(200);
			assertThat(call(client, "get_ranking", Map.of("clientOf", atCap.json())).isError()).isNotEqualTo(true);
		}
	}

	@Test
	void everySimulationBoundaryIsAcceptedOrRejectedLikeTheRestEndpointNamingTheSameField() throws Exception {
		try (McpSyncClient client = client()) {
			client.initialize();
			for (Case argument : simulationCases()) {
				MockHttpServletResponse rest = mockMvc.perform(get("/api/v1/simulations")
						.param("instrumentId", Long.toString(bankId)).param(argument.name(), argument.value()))
						.andReturn().getResponse();
				Map<String, Object> arguments = new LinkedHashMap<>();
				arguments.put("instrumentId", bankId);
				arguments.put(argument.name(), argument.json());
				CallToolResult tool = call(client, "simulate_instrument", arguments);

				assertSameVerdict("simulate_instrument " + argument, rest, tool);
			}
		}
	}

	@Test
	void theInstrumentIdIsCheckedLikeTheRestEndpoint() throws Exception {
		try (McpSyncClient client = client()) {
			client.initialize();

			MockHttpServletResponse unknown = mockMvc.perform(get("/api/v1/simulations").param("instrumentId",
					"987654321")).andReturn().getResponse();
			MockHttpServletResponse missing = mockMvc.perform(get("/api/v1/simulations")).andReturn().getResponse();

			assertSameVerdict("unknown id", unknown, call(client, "simulate_instrument",
					Map.of("instrumentId", 987654321L)));
			assertSameVerdict("missing id", missing, call(client, "simulate_instrument", Map.of()));
			assertThat(unknown.getStatus()).isEqualTo(404);
			assertThat(missing.getStatus()).isEqualTo(400);
		}
	}

	@Test
	void theRankingToolReadsTheSameAnswerAsTheRestEndpoint() throws Exception {
		try (McpSyncClient client = client()) {
			client.initialize();
			for (String[] query : new String[][] { { "20000", "30" }, { "1000000", "30" }, { "500000", "400" } }) {
				MockHttpServletRequestBuilder request = get("/api/v1/ranking").param("amount", query[0])
						.param("days", query[1]).param("limit", "10");
				JsonNode rest = JSON.readTree(mockMvc.perform(request).andReturn().getResponse().getContentAsString());

				JsonNode tool = structured(call(client, "get_ranking",
						Map.of("amount", new BigDecimal(query[0]), "days", Integer.parseInt(query[1]), "limit", 10)));

				assertThat(tool.get("ranked").size()).isPositive();
				assertThat(tool.get("ranked")).as("every field of every row, as the REST API presents it")
						.isEqualTo(rest.get("ranked"));
				assertThat(tool.get("inflation")).isEqualTo(rest.get("inflation"));
				assertThat(tool.get("dollarMep")).isEqualTo(rest.get("dollarMep"));
				assertThat(tool.get("request")).isEqualTo(rest.get("request"));
				assertThat(tool.at("/totals/ranked")).isEqualTo(rest.at("/totals/ranked"));
				assertThat(tool.at("/totals/ineligible")).isEqualTo(rest.at("/totals/ineligible"));
				assertThat(tool.at("/totals/excluded")).isEqualTo(rest.at("/totals/excluded"));
			}
		}
	}

	@Test
	void theSimulationToolReadsTheSameAnswerAsTheRestEndpoint() throws Exception {
		try (McpSyncClient client = client()) {
			client.initialize();
			JsonNode rest = JSON.readTree(mockMvc.perform(get("/api/v1/simulations")
					.param("instrumentId", Long.toString(bankId)).param("amount", "250000").param("days", "90"))
					.andReturn().getResponse().getContentAsString());

			JsonNode tool = structured(call(client, "simulate_instrument",
					Map.of("instrumentId", bankId, "amount", 250000, "days", 90)));

			assertThat(tool.get("status")).isEqualTo(rest.get("status"));
			assertThat(tool.get("entry")).as("the entry, as the REST API presents it").isEqualTo(rest.get("entry"));
			assertThat(tool.get("dollarMep")).isEqualTo(rest.get("dollarMep"));
		}
	}

	private static void assertSameVerdict(String what, MockHttpServletResponse rest, CallToolResult tool)
			throws Exception {
		boolean restAccepted = rest.getStatus() == 200;
		assertThat(rest.getStatus()).as(what + ": REST answers 200 or a rejection").isIn(200, 400, 404);
		assertThat(tool.isError() == null || !tool.isError()).as(what + ": REST " + rest.getStatus()
				+ ", tool " + tool).isEqualTo(restAccepted);
		if (!restAccepted) {
			JsonNode problem = JSON.readTree(rest.getContentAsString());
			assertThat(structured(tool).get("argument").asString()).as(what + ": the field it names")
					.isEqualTo(problem.get("parameter").asString());
		}
	}
}
