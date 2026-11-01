package ar.rindetuplata.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.rindetuplata.ranking.RankingQuery;
import ar.rindetuplata.source.macro.BcraMonetariasClient;
import ar.rindetuplata.source.plazofijo.ArgentinaDatosPlazoFijoAdapter;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.InitializeResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * T6e (S6, L32 c): the MCP server of the API driven through the SDK's own client over real HTTP. It covers the
 * handshake, the tool list, each of the five read-only tools (every answer states its data date and source), the
 * argument errors (a tool error that names the argument, never a stack trace) and the stateless, cookie-free surface
 * of {@code POST /mcp}.
 */
class McpServerApiTest extends McpIntegrationTest {

	private static final String PLAZO_FIJO_SOURCE = ArgentinaDatosPlazoFijoAdapter.SOURCE.id();
	private static final String INFLATION_SOURCE = BcraMonetariasClient.SOURCE.id();

	private static final String INITIALIZE = """
			{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",\
			"capabilities":{},"clientInfo":{"name":"raw-test","version":"1"}}}""";

	// --- handshake and tool list ----------------------------------------------------------------------------------

	@Test
	void initializeNamesTheServerAndAdvertisesOnlyTools() {
		try (McpSyncClient client = client()) {
			InitializeResult init = client.initialize();

			assertThat(init.serverInfo().name()).isEqualTo("rindetuplata");
			assertThat(init.capabilities().tools()).as("tools are advertised").isNotNull();
			assertThat(init.capabilities().resources()).isNull();
			assertThat(init.capabilities().prompts()).isNull();
			assertThat(init.instructions()).contains("read-only").contains("data date").contains("source");
		}
	}

	@Test
	void toolsListNamesTheFiveReadOnlyToolsWithTheirCaps() {
		try (McpSyncClient client = client()) {
			client.initialize();

			List<Tool> tools = client.listTools().tools();

			assertThat(tools.stream().map(Tool::name).collect(Collectors.toSet())).isEqualTo(Set.of("get_ranking",
					"simulate_instrument", "get_instrument_history", "list_monthly_reports", "get_monthly_report"));
			for (Tool tool : tools) {
				assertThat(tool.description()).as(tool.name()).isNotBlank();
				assertThat(tool.annotations().readOnlyHint()).as(tool.name()).isTrue();
				assertThat(tool.annotations().destructiveHint()).as(tool.name()).isFalse();
				assertThat(tool.annotations().openWorldHint()).as(tool.name()).isFalse();
				assertThat(tool.inputSchema().get("type")).as(tool.name()).isEqualTo("object");
			}
			JsonNode ranking = JSON.valueToTree(tool(tools, "get_ranking").inputSchema());
			assertThat(propertyNames(ranking)).containsExactlyInAnyOrder("amount", "days", "kind", "clientOf", "asOf",
					"limit", "groupFunds");
			assertThat(ranking.at("/properties/days/maximum").asInt()).isEqualTo(RankingQuery.MAX_DAYS);
			assertThat(ranking.at("/properties/limit/maximum").asInt()).isEqualTo(RankingQuery.MAX_LIMIT);
			assertThat(ranking.at("/properties/kind/items/enum").size()).isEqualTo(4);
			JsonNode simulate = JSON.valueToTree(tool(tools, "simulate_instrument").inputSchema());
			assertThat(simulate.get("required").get(0).asString()).isEqualTo("instrumentId");
			JsonNode month = JSON.valueToTree(tool(tools, "get_monthly_report").inputSchema());
			assertThat(month.get("required").get(0).asString()).isEqualTo("month");
		}
	}

	// --- ranking --------------------------------------------------------------------------------------------------

	@Test
	void theRankingToolAnswersTheRankedRowsWithTheirDataDateAndSource() throws Exception {
		try (McpSyncClient client = client()) {
			client.initialize();

			CallToolResult result = call(client, "get_ranking",
					Map.of("amount", 1000000, "days", 30, "kind", List.of("PLAZO_FIJO"), "limit", 5));

			assertThat(result.isError()).isFalse();
			JsonNode body = structured(result);
			assertThat(JSON.readTree(text(result))).as("the text block is the same JSON").isEqualTo(body);
			assertThat(body.get("asOf").asString()).isEqualTo("2026-10-20");
			assertThat(body.get("dataDate").asString()).isEqualTo("2026-10-14");
			assertThat(body.at("/sources/0/id").asString()).isEqualTo(PLAZO_FIJO_SOURCE);
			assertThat(body.at("/sources/0/displayName").asString()).isNotBlank();
			assertThat(body.at("/request/amount").decimalValue()).isEqualByComparingTo("1000000");
			assertThat(body.at("/request/days").asInt()).isEqualTo(30);
			assertThat(body.at("/request/limit").asInt()).isEqualTo(5);
			assertThat(body.at("/request/kinds/0").asString()).isEqualTo("PLAZO_FIJO");
			assertThat(body.at("/inflation/index").asString()).isEqualTo("REM_12M_EXPECTED");
			assertThat(body.at("/inflation/observationDate").asString()).isEqualTo("2026-10-14");
			assertThat(body.at("/totals/ranked").asInt()).isEqualTo(1);
			JsonNode row = body.at("/ranked/0");
			assertThat(body.get("ranked").size()).isEqualTo(1);
			assertThat(row.get("id").asString()).isEqualTo(Long.toString(bankId));
			assertThat(row.get("kind").asString()).isEqualTo("PLAZO_FIJO");
			assertThat(row.get("name").asString()).isEqualTo(BANK);
			assertThat(row.at("/source/id").asString()).isEqualTo(PLAZO_FIJO_SOURCE);
			assertThat(row.get("dataDate").asString()).isEqualTo("2026-10-14");
			assertThat(row.get("captureDate").asString()).isEqualTo("2026-10-14");
			// 36% TNA (the non-client rate) over 30 days: 1,000,000 * 0.36 * 30 / 365 = 29,589.04.
			assertThat(row.at("/rateUsed/fraction").decimalValue()).isEqualByComparingTo("0.36");
			assertThat(row.get("finalAmount").decimalValue()).isEqualByComparingTo("1029589.04");
			assertThat(row.get("earned").decimalValue()).isEqualByComparingTo("29589.04");
			assertThat(row.get("nominalReturn").decimalValue()).isEqualByComparingTo("0.029589");
			assertThat(row.get("realReturn").decimalValue()).isLessThan(row.get("nominalReturn").decimalValue());
		}
	}

	@Test
	void theRankingToolWorksWithoutArguments() {
		try (McpSyncClient client = client()) {
			client.initialize();

			CallToolResult result = call(client, "get_ranking", Map.of());

			assertThat(result.isError()).isFalse();
			JsonNode body = structured(result);
			assertThat(body.at("/request/days").asInt()).isEqualTo(RankingQuery.DEFAULT_DAYS);
			assertThat(body.at("/request/limit").asInt()).isEqualTo(RankingQuery.DEFAULT_LIMIT);
			assertThat(body.at("/request/amount").decimalValue())
					.isEqualByComparingTo(RankingQuery.DEFAULT_AMOUNT.presented());
			assertThat(body.get("ranked").findValuesAsString("id")).contains(Long.toString(bankId));
		}
	}

	// --- simulation -----------------------------------------------------------------------------------------------

	@Test
	void theSimulationToolAnswersOneInstrumentAndTheClientRateChangesIt() {
		try (McpSyncClient client = client()) {
			client.initialize();

			JsonNode plain = structured(call(client, "simulate_instrument",
					Map.of("instrumentId", bankId, "amount", 1000000, "days", 30)));
			JsonNode asClient = structured(call(client, "simulate_instrument",
					Map.of("instrumentId", bankId, "amount", 1000000, "days", 30, "client", true)));

			assertThat(plain.get("status").asString()).isEqualTo("SIMULATED");
			assertThat(plain.get("asOf").asString()).isEqualTo("2026-10-20");
			assertThat(plain.get("dataDate").asString()).isEqualTo("2026-10-14");
			assertThat(plain.at("/source/id").asString()).isEqualTo(PLAZO_FIJO_SOURCE);
			assertThat(plain.at("/entry/name").asString()).isEqualTo(BANK);
			assertThat(plain.at("/entry/finalAmount").decimalValue()).isEqualByComparingTo("1029589.04");
			// 40% TNA of a client: 1,000,000 * 0.40 * 30 / 365 = 32,876.71.
			assertThat(asClient.at("/entry/finalAmount").decimalValue()).isEqualByComparingTo("1032876.71");
			assertThat(asClient.at("/entry/rateUsed/fraction").decimalValue()).isEqualByComparingTo("0.40");
		}
	}

	@Test
	void anUnknownInstrumentIsAToolErrorNamingInstrumentId() {
		try (McpSyncClient client = client()) {
			client.initialize();

			CallToolResult result = call(client, "simulate_instrument", Map.of("instrumentId", 987654321L));

			assertToolError(result, "not_found", "instrumentId");
			assertThat(text(result)).contains("987654321");
		}
	}

	@Test
	void theInstrumentIdIsRequiredBySimulationAndHistory() {
		try (McpSyncClient client = client()) {
			client.initialize();

			assertToolError(call(client, "simulate_instrument", Map.of("days", 30)), "invalid_argument",
					"instrumentId");
			assertToolError(call(client, "get_instrument_history", Map.of()), "invalid_argument", "instrumentId");
		}
	}

	// --- history --------------------------------------------------------------------------------------------------

	@Test
	void theHistoryToolAnswersThePointsOfTheWindowWithTheLastDataDateAndSource() {
		try (McpSyncClient client = client()) {
			client.initialize();

			JsonNode body = structured(call(client, "get_instrument_history", Map.of("instrumentId", bankId)));

			assertThat(body.get("dataDate").asString()).isEqualTo("2026-10-14");
			assertThat(body.at("/source/id").asString()).isEqualTo(PLAZO_FIJO_SOURCE);
			assertThat(body.at("/history/id").asString()).isEqualTo(Long.toString(bankId));
			assertThat(body.at("/history/displayName").asString()).isEqualTo(BANK);
			assertThat(body.at("/history/from").asString()).isEqualTo("2026-07-23");
			assertThat(body.at("/history/to").asString()).isEqualTo("2026-10-20");
			assertThat(body.at("/history/points").size()).isEqualTo(2);
		}
	}

	@Test
	void theHistoryToolHonorsTheRangeAndAnEmptyRangeHasNoDataDate() {
		try (McpSyncClient client = client()) {
			client.initialize();

			JsonNode one = structured(call(client, "get_instrument_history",
					Map.of("instrumentId", bankId, "from", "2026-10-14", "to", "2026-10-14")));
			JsonNode none = structured(call(client, "get_instrument_history",
					Map.of("instrumentId", bankId, "from", "2020-01-01", "to", "2020-01-10")));

			assertThat(one.at("/history/points").size()).isEqualTo(1);
			assertThat(one.get("dataDate").asString()).isEqualTo("2026-10-14");
			assertThat(none.at("/history/points").size()).isZero();
			assertThat(none.get("dataDate").isNull()).isTrue();
			assertThat(none.at("/source/id").asString()).as("the instrument's source is known without points")
					.isEqualTo(PLAZO_FIJO_SOURCE);
		}
	}

	@Test
	void theHistoryToolNamesTheArgumentOfARejectedRangeOrId() {
		try (McpSyncClient client = client()) {
			client.initialize();

			assertToolError(call(client, "get_instrument_history",
					Map.of("instrumentId", bankId, "from", "2026-10-14", "to", "2026-10-01")),
					"invalid_argument", "from");
			assertToolError(call(client, "get_instrument_history",
					Map.of("instrumentId", bankId, "from", "2024-01-01", "to", "2026-10-01")),
					"invalid_argument", "to");
			assertToolError(call(client, "get_instrument_history", Map.of("instrumentId", 987654321L)), "not_found",
					"instrumentId");
			assertToolError(call(client, "get_instrument_history",
					Map.of("instrumentId", bankId, "from", "14/10/2026")), "invalid_argument", "from");
		}
	}

	@Test
	void aDateArgumentOutsideYear1To9999IsAnInvalidArgumentAndTheFirstAndLastDatesAreAccepted() {
		try (McpSyncClient client = client()) {
			client.initialize();

			for (String extreme : new String[] { "+999999999-12-31", "-999999999-01-01" }) {
				assertToolError(call(client, "get_ranking", Map.of("asOf", extreme)), "invalid_argument", "asOf");
				assertToolError(call(client, "get_instrument_history",
						Map.of("instrumentId", bankId, "from", extreme)), "invalid_argument", "from");
				assertToolError(call(client, "get_instrument_history",
						Map.of("instrumentId", bankId, "to", extreme)), "invalid_argument", "to");
			}
			assertThat(call(client, "get_ranking", Map.of("asOf", "0001-01-01")).isError()).isNotEqualTo(true);
			assertThat(call(client, "get_ranking", Map.of("asOf", "9999-12-31")).isError()).isNotEqualTo(true);
			assertThat(call(client, "get_instrument_history",
					Map.of("instrumentId", bankId, "from", "0001-01-01", "to", "0001-01-10")).isError())
					.isNotEqualTo(true);
			assertThat(call(client, "get_instrument_history",
					Map.of("instrumentId", bankId, "from", "9999-12-01", "to", "9999-12-31")).isError())
					.isNotEqualTo(true);
		}
	}

	// --- monthly reports ------------------------------------------------------------------------------------------

	@Test
	void theReportListToolNamesTheClosedMonthsWithTheInflationDateAndSource() {
		try (McpSyncClient client = client()) {
			client.initialize();

			CallToolResult result = call(client, "list_monthly_reports", Map.of());

			assertThat(result.isError()).isFalse();
			JsonNode body = structured(result);
			assertThat(body.at("/months").size()).isEqualTo(1);
			assertThat(body.at("/months/0/month").asString()).isEqualTo("2026-09");
			assertThat(body.at("/months/0/monthlyInflation").decimalValue()).isEqualByComparingTo("0.019");
			assertThat(body.at("/months/0/ranked").asInt()).isEqualTo(1);
			assertThat(body.at("/months/0/beatInflation").asInt()).isEqualTo(1);
			assertThat(body.get("dataDate").asString()).isEqualTo("2026-09-30");
			assertThat(body.at("/source/id").asString()).isEqualTo(INFLATION_SOURCE);
		}
	}

	@Test
	void theReportToolAnswersTheRowsOfAClosedMonth() {
		try (McpSyncClient client = client()) {
			client.initialize();

			CallToolResult result = call(client, "get_monthly_report", Map.of("month", "2026-09"));

			assertThat(result.isError()).isFalse();
			JsonNode body = structured(result);
			assertThat(body.get("dataDate").asString()).isEqualTo("2026-09-30");
			assertThat(body.at("/source/id").asString()).isEqualTo(INFLATION_SOURCE);
			assertThat(body.at("/report/month").asString()).isEqualTo("2026-09");
			assertThat(body.at("/report/inflation/index").asString()).isEqualTo("INDEC_CPI_MONTHLY");
			assertThat(body.at("/report/inflation/value").decimalValue()).isEqualByComparingTo("0.019");
			assertThat(body.at("/report/rows").size()).isEqualTo(1);
			assertThat(body.at("/report/rows/0/name").asString()).isEqualTo("Alpha Wallet");
			assertThat(body.at("/report/rows/0/source/id").asString()).isEqualTo("uala-cuenta-remunerada");
			assertThat(body.at("/report/rows/0/beatInflation").asBoolean()).isTrue();
			assertThat(body.at("/report/totals/beatInflation").asInt()).isEqualTo(1);
		}
	}

	@Test
	void theReportToolNamesTheMonthItRejectsOrCannotFind() {
		try (McpSyncClient client = client()) {
			client.initialize();

			for (String malformed : new String[] { "2026-13", "2026-9", "septiembre", "2026-09-01" }) {
				assertToolError(call(client, "get_monthly_report", Map.of("month", malformed)), "invalid_argument",
						"month");
			}
			assertToolError(call(client, "get_monthly_report", Map.of()), "invalid_argument", "month");
			CallToolResult noInflation = call(client, "get_monthly_report", Map.of("month", "2025-12"));
			assertToolError(noInflation, "not_found", "month");
			assertThat(text(noInflation)).contains("INDEC");
			assertToolError(call(client, "get_monthly_report", Map.of("month", "2026-10")), "not_found", "month");
		}
	}

	// --- argument errors ------------------------------------------------------------------------------------------

	@Test
	void anArgumentErrorIsAToolErrorThatNamesTheArgumentAndNeverShowsAStackTrace() {
		try (McpSyncClient client = client()) {
			client.initialize();

			CallToolResult tooLong = call(client, "get_ranking", Map.of("days", 2000000000));
			CallToolResult notANumber = call(client, "get_ranking", Map.of("days", "abc"));
			CallToolResult fraction = call(client, "get_ranking", Map.of("days", 2.5));
			CallToolResult badKind = call(client, "get_ranking", Map.of("kind", List.of("BOGUS")));
			CallToolResult badDate = call(client, "get_ranking", Map.of("asOf", "yesterday"));
			CallToolResult negative = call(client, "get_ranking", Map.of("amount", -5));
			CallToolResult huge = call(client, "get_ranking", Map.of("amount", "1e999999999"));
			CallToolResult unknown = call(client, "get_ranking", Map.of("dayz", 30));

			assertToolError(tooLong, "invalid_argument", "days");
			assertThat(text(tooLong)).contains(Integer.toString(RankingQuery.MAX_DAYS)).contains("2000000000");
			assertToolError(notANumber, "invalid_argument", "days");
			assertToolError(fraction, "invalid_argument", "days");
			assertToolError(badKind, "invalid_argument", "kind");
			assertThat(text(badKind)).contains("BOGUS").contains("PLAZO_FIJO");
			assertToolError(badDate, "invalid_argument", "asOf");
			assertToolError(negative, "invalid_argument", "amount");
			assertToolError(huge, "invalid_argument", "amount");
			assertThat(text(huge).length()).as("a huge exponent is never expanded into digits").isLessThan(300);
			assertToolError(unknown, "invalid_argument", "dayz");
			for (CallToolResult result : List.of(tooLong, notANumber, fraction, badKind, badDate, negative, huge,
					unknown)) {
				assertThat(text(result)).doesNotContain("Exception").doesNotContain("\tat ")
						.doesNotContain("ar.rindetuplata").doesNotContain("java.");
			}
		}
	}

	@Test
	void anUnknownToolIsAProtocolError() {
		try (McpSyncClient client = client()) {
			client.initialize();

			assertThatThrownBy(() -> call(client, "delete_everything", Map.of())).isInstanceOf(McpError.class)
					.hasMessageContaining("Unknown tool");
		}
	}

	// --- the HTTP surface -----------------------------------------------------------------------------------------

	@Test
	void thePostIsStatelessCookieFreeAndNeedsNoCsrfToken() throws Exception {
		int sessionsBefore = sessionCount();

		HttpResponse<String> response = post("/mcp", INITIALIZE);

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
				type -> assertThat(type).contains("application/json"));
		// The SPA CSRF handler of the shared chain hands an XSRF-TOKEN cookie to any caller without one, the REST
		// GETs included; what /mcp must not do is open a session or add any other cookie.
		assertThat(response.headers().allValues("Set-Cookie")).allSatisfy(cookie -> assertThat(cookie)
				.startsWith("XSRF-TOKEN=").doesNotContain("SESSION"));
		assertThat(get("/api/v1/ranking").headers().allValues("Set-Cookie").stream().map(c -> c.split("=")[0]))
				.as("no more cookies than a REST read").containsAll(response.headers().allValues("Set-Cookie")
						.stream().map(c -> c.split("=")[0]).toList());
		assertThat(response.headers().map()).doesNotContainKey("Mcp-Session-Id").doesNotContainKey("mcp-session-id");
		assertThat(JSON.readTree(response.body()).at("/result/serverInfo/name").asString())
				.isEqualTo("rindetuplata");
		assertThat(sessionCount()).as("no HTTP session was created").isEqualTo(sessionsBefore);
	}

	@Test
	void onlyThePostToTheExactPathIsOpen() throws Exception {
		HttpClient http = HttpClient.newHttpClient();
		for (String method : new String[] { "GET", "PUT", "DELETE", "PATCH" }) {
			HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/mcp"))
					.method(method, HttpRequest.BodyPublishers.ofString("")).build(), HttpResponse.BodyHandlers.ofString());
			assertThat(response.statusCode()).as(method + " /mcp").isIn(401, 403, 405);
		}
		assertThat(post("/mcp/other", INITIALIZE).statusCode()).as("POST /mcp/other").isIn(401, 403, 404);
		assertThat(post("/mcp/", INITIALIZE).statusCode()).as("POST /mcp/").isIn(401, 403, 404);
	}

	@Test
	void twoClientsNeedNoServerSideSession() {
		try (McpSyncClient first = client(); McpSyncClient second = client()) {
			first.initialize();
			second.initialize();

			assertThat(call(first, "list_monthly_reports", Map.of()).isError()).isFalse();
			assertThat(call(second, "list_monthly_reports", Map.of()).isError()).isFalse();
		}
	}

	@Test
	void aToolCallNeedsNoInitializeRequestBecauseNothingIsKeptBetweenRequests() throws Exception {
		HttpResponse<String> response = post("/mcp", """
				{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"list_monthly_reports","arguments":{}}}""");

		assertThat(response.statusCode()).isEqualTo(200);
		JsonNode body = JSON.readTree(response.body());
		assertThat(body.get("id").asInt()).isEqualTo(7);
		assertThat(body.at("/result/isError").asBoolean()).isFalse();
		assertThat(body.at("/result/structuredContent/months/0/month").asString()).isEqualTo("2026-09");
	}

	// --- helpers --------------------------------------------------------------------------------------------------

	private URI uri(String path) {
		return URI.create("http://127.0.0.1:" + port + path);
	}

	private HttpResponse<String> get(String path) throws Exception {
		return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri(path)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> post(String path, String body) throws Exception {
		return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri(path))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json, text/event-stream")
				.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}

	private int sessionCount() {
		return jdbc.sql("select count(*) from spring_session").query(Integer.class).single();
	}

	private static Tool tool(List<Tool> tools, String name) {
		return tools.stream().filter(tool -> tool.name().equals(name)).findFirst().orElseThrow();
	}

	private static Set<String> propertyNames(JsonNode schema) {
		return schema.get("properties").propertyNames().stream().collect(Collectors.toSet());
	}

	/** A tool error: {@code isError}, and the structured content says which argument and why in one word. */
	static void assertToolError(CallToolResult result, String error, String argument) {
		assertThat(result.isError()).as("isError of %s", result).isTrue();
		JsonNode body = structured(result);
		assertThat(body.get("error").asString()).isEqualTo(error);
		assertThat(body.get("argument").asString()).isEqualTo(argument);
		assertThat(body.get("message").asString()).contains(argument);
		assertThat(text(result)).contains(argument);
	}
}
