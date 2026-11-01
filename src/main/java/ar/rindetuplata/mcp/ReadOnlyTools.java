package ar.rindetuplata.mcp;

import ar.rindetuplata.api.ApiException;
import ar.rindetuplata.api.instrument.FundQuotePoint;
import ar.rindetuplata.api.instrument.InstrumentHistory;
import ar.rindetuplata.api.instrument.InstrumentService;
import ar.rindetuplata.api.instrument.SnapshotPoint;
import ar.rindetuplata.api.report.ReportMonthView;
import ar.rindetuplata.api.JsonScales;
import ar.rindetuplata.domain.Money;
import ar.rindetuplata.ranking.InstrumentNotFoundException;
import ar.rindetuplata.ranking.PublicRanking;
import ar.rindetuplata.ranking.RankedKind;
import ar.rindetuplata.ranking.RankingQuery;
import ar.rindetuplata.ranking.RankingService;
import ar.rindetuplata.ranking.SourceRegistry;
import ar.rindetuplata.report.MonthlyReportService;
import ar.rindetuplata.report.MonthlyReportUnavailableException;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The read-only tools of the MCP server (T6e, S6, L32 c), over the very services the REST API uses:
 * {@link PublicRanking} and {@link RankingService} for the ranking and the simulation, {@link InstrumentService}
 * for the history and {@link MonthlyReportService} for the monthly reports. No tool writes. Every answer states the
 * {@code dataDate} it rests on and its {@code source} ({@link ToolResults}); a bad or unknown argument is an MCP
 * tool error that names it ({@link ToolArgumentException}), and anything unexpected is a generic tool error with the
 * detail only in the log, never a stack trace in the answer.
 */
@Component
class ReadOnlyTools {

	private static final Logger LOG = LoggerFactory.getLogger(ReadOnlyTools.class);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
	};

	private static final Pattern MONTH = Pattern.compile("\\d{4}-(?:0[1-9]|1[0-2])");

	private static final String READ_ONLY = "Read-only. The answer states the dataDate it rests on and its source.";

	private final PublicRanking publicRanking;
	private final RankingService ranking;
	private final InstrumentService instruments;
	private final MonthlyReportService reports;
	private final Clock clock;

	ReadOnlyTools(PublicRanking publicRanking, RankingService ranking, InstrumentService instruments,
			MonthlyReportService reports, Clock clock) {
		this.publicRanking = publicRanking;
		this.ranking = ranking;
		this.instruments = instruments;
		this.reports = reports;
		this.clock = clock;
	}

	List<SyncToolSpecification> specifications() {
		return List.of(
				spec("get_ranking", "Personalized ranking",
						"Ranks the Argentine savings instruments (plazo fijo by bank, money-market funds, remunerated "
								+ "wallets, crypto yields) for an amount and a number of days, best final amount first, with the "
								+ "real return against expected inflation and the break-even against the dollar MEP. Same "
								+ "parameters and caps as GET /api/v1/ranking. " + READ_ONLY,
						rankingSchema(), Set.of("amount", "days", "kind", "clientOf", "asOf", "limit", "groupFunds"),
						this::ranking),
				spec("simulate_instrument", "Simulate one instrument",
						"Simulates one instrument (an id from get_ranking) for an amount and a number of days. The "
								+ "status is SIMULATED with its entry, INELIGIBLE or EXCLUDED with the reason. Same parameters "
								+ "as GET /api/v1/simulations. " + READ_ONLY,
						simulationSchema(), Set.of("instrumentId", "amount", "days", "client"), this::simulation),
				spec("get_instrument_history", "Instrument history",
						"The stored history of one instrument over a date range (default: the last "
								+ InstrumentService.DEFAULT_WINDOW_DAYS + " days, at most " + InstrumentService.MAX_WINDOW_DAYS
								+ " days): its rate snapshots or fund quotes, and the INDEC monthly inflation of the range. "
								+ READ_ONLY,
						historySchema(), Set.of("instrumentId", "from", "to"), this::history),
				spec("list_monthly_reports", "Monthly reports",
						"The closed months that have a \"who beat inflation\" report, newest first, with the INDEC "
								+ "inflation of each. Empty until a month is closed, INDEC has published it and the stored "
								+ "history covers it. " + READ_ONLY,
						schema(Map.of(), List.of()), Set.of(), this::reportList),
				spec("get_monthly_report", "Monthly report",
						"Who beat inflation in a closed month: the nominal and real return each instrument delivered "
								+ "against the INDEC inflation of that month, best first, and the instruments that could not be "
								+ "measured with the reason. " + READ_ONLY,
						monthSchema(), Set.of("month"), this::report));
	}

	// --- the tools ------------------------------------------------------------------------------------------------

	private Object ranking(ToolArguments arguments) {
		RankingQuery query = new RankingQuery(arguments.amount(), arguments.days(), arguments.clientOf(),
				arguments.kinds(), orToday(arguments.date("asOf")), arguments.limit(), arguments.flag("groupFunds", true));
		String version = publicRanking.dataVersion();
		return McpPresenter.ranking(publicRanking.rank(version, query));
	}

	private Object simulation(ToolArguments arguments) {
		long instrumentId = arguments.requiredId("instrumentId");
		Money amount = arguments.amount();
		int days = arguments.days();
		boolean client = arguments.flag("client", false);
		LocalDate asOf = LocalDate.now(clock);
		try {
			return McpPresenter.simulation(ranking.simulate(instrumentId, amount, days, client, asOf), asOf);
		}
		catch (InstrumentNotFoundException e) {
			throw ToolArgumentException.notFound("instrumentId", e.getMessage());
		}
	}

	private Object history(ToolArguments arguments) {
		long instrumentId = arguments.requiredId("instrumentId");
		LocalDate from = arguments.date("from");
		LocalDate to = arguments.date("to");
		InstrumentHistory history;
		try {
			history = instruments.history(instrumentId, from, to);
		}
		catch (ApiException e) {
			// The service names the path variable "id"; the tool calls it instrumentId.
			String argument = "id".equals(e.parameter()) ? "instrumentId" : e.parameter();
			throw e.status().value() == 404 ? ToolArgumentException.notFound(argument, e.getMessage())
					: ToolArgumentException.invalid(argument, e.getMessage());
		}
		LocalDate dataDate = Stream.concat(history.points().stream().map(SnapshotPoint::dataDate),
				history.quotes().stream().map(FundQuotePoint::quoteDate))
				.filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
		return new ToolResults.History(dataDate, SourceRegistry.of(history.sourceId()), history);
	}

	private Object reportList(ToolArguments arguments) {
		List<MonthlyReportService.MonthSummary> months = reports.months();
		if (months.isEmpty()) {
			return new ToolResults.ReportList(null, null, List.of());
		}
		var newest = months.get(0).inflation();
		return new ToolResults.ReportList(newest.observationDate(), SourceRegistry.of(newest.sourceId()),
				months.stream().map(summary -> new ReportMonthView(summary.month().toString(),
						JsonScales.rate(summary.inflation().value()), summary.ranked(), summary.beatInflation()))
						.toList());
	}

	private Object report(ToolArguments arguments) {
		String month = arguments.requiredText("month");
		if (!MONTH.matcher(month).matches()) {
			throw ToolArgumentException.invalid("month", "must be a month as yyyy-MM: '" + month + "'");
		}
		try {
			return McpPresenter.report(reports.report(YearMonth.parse(month)));
		}
		catch (MonthlyReportUnavailableException e) {
			throw ToolArgumentException.notFound("month", e.getMessage());
		}
	}

	private LocalDate orToday(LocalDate date) {
		return date == null ? LocalDate.now(clock) : date;
	}

	// --- plumbing -------------------------------------------------------------------------------------------------

	private interface ToolBody {

		Object run(ToolArguments arguments);
	}

	private SyncToolSpecification spec(String name, String title, String description, Map<String, Object> schema,
			Set<String> allowed, ToolBody body) {
		Tool tool = Tool.builder().name(name).title(title).description(description).inputSchema(schema)
				.annotations(new ToolAnnotations(title, true, false, true, false, null)).build();
		BiFunction<McpTransportContext, CallToolRequest, CallToolResult> handler = (context, request) -> {
			try {
				return success(body.run(new ToolArguments(request.arguments(), allowed)));
			}
			catch (ToolArgumentException e) {
				return failure(e.error(), e.argument(), e.getMessage());
			}
			catch (RuntimeException e) {
				LOG.error("MCP tool {} failed", name, e);
				return failure("internal_error", null, "The tool could not answer; try again later.");
			}
		};
		return SyncToolSpecification.builder().tool(tool).callHandler(handler).build();
	}

	private static CallToolResult success(Object result) {
		return CallToolResult.builder().addTextContent(JSON.writeValueAsString(result))
				.structuredContent(JSON.convertValue(result, MAP)).isError(false).build();
	}

	private static CallToolResult failure(String error, String argument, String message) {
		Map<String, Object> structured = new LinkedHashMap<>();
		structured.put("error", error);
		if (argument != null) {
			structured.put("argument", argument);
		}
		structured.put("message", message);
		return CallToolResult.builder().addTextContent(message).structuredContent(structured).isError(true).build();
	}

	// --- input schemas (the bounds come from RankingQuery, never from literals) -----------------------------------

	private static Map<String, Object> rankingSchema() {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("amount", amountProperty());
		properties.put("days", daysProperty());
		properties.put("kind", Map.of("type", "array", "description",
				"Kinds to rank; all when omitted.", "items", Map.of("type", "string", "enum",
						Arrays.stream(RankedKind.values()).map(Enum::name).toList())));
		properties.put("clientOf", Map.of("type", "array", "description",
				"Instrument ids of the banks the user is a client of, to use their client rate; at most "
						+ RankingQuery.MAX_CLIENT_OF + ".", "maxItems", RankingQuery.MAX_CLIENT_OF, "items",
				Map.of("type", "integer")));
		properties.put("asOf", dateProperty("Reference date as yyyy-MM-dd; today in Argentina when omitted."));
		properties.put("limit", Map.of("type", "integer", "minimum", 1, "maximum", RankingQuery.MAX_LIMIT, "default",
				RankingQuery.DEFAULT_LIMIT, "description", "Rows to return."));
		properties.put("groupFunds", Map.of("type", "boolean", "default", true, "description",
				"True folds the classes of one fund into its best class."));
		return schema(properties, List.of());
	}

	private static Map<String, Object> simulationSchema() {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("instrumentId", Map.of("type", "integer", "description",
				"Instrument id, as returned by get_ranking."));
		properties.put("amount", amountProperty());
		properties.put("days", daysProperty());
		properties.put("client", Map.of("type", "boolean", "default", false, "description",
				"True uses the client rate of a bank."));
		return schema(properties, List.of("instrumentId"));
	}

	private static Map<String, Object> historySchema() {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("instrumentId", Map.of("type", "integer", "description",
				"Instrument id, as returned by get_ranking."));
		properties.put("from", dateProperty("First day as yyyy-MM-dd; the default window ends at 'to' and spans "
				+ InstrumentService.DEFAULT_WINDOW_DAYS + " days when omitted."));
		properties.put("to", dateProperty("Last day as yyyy-MM-dd; today in Argentina when omitted."));
		return schema(properties, List.of("instrumentId"));
	}

	private static Map<String, Object> monthSchema() {
		return schema(Map.of("month", Map.of("type", "string", "pattern", MONTH.pattern(), "description",
				"The closed month as yyyy-MM, for example 2026-10.")), List.of("month"));
	}

	private static Map<String, Object> amountProperty() {
		return Map.of("type", "number", "exclusiveMinimum", 0, "maximum", RankingQuery.MAX_AMOUNT, "default",
				RankingQuery.DEFAULT_AMOUNT.value(), "description", "Amount in ARS.");
	}

	private static Map<String, Object> daysProperty() {
		return Map.of("type", "integer", "minimum", 1, "maximum", RankingQuery.MAX_DAYS, "default",
				RankingQuery.DEFAULT_DAYS, "description", "Horizon in days.");
	}

	private static Map<String, Object> dateProperty(String description) {
		return Map.of("type", "string", "format", "date", "description", description);
	}

	private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", properties);
		schema.put("required", required);
		schema.put("additionalProperties", false);
		return schema;
	}
}
