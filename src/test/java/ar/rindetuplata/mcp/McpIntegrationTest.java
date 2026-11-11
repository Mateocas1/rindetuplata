package ar.rindetuplata.mcp;

import ar.rindetuplata.api.ApiTestConfiguration;
import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Fixtures;
import ar.rindetuplata.source.crypto.ArgentinaDatosCryptoAdapter;
import ar.rindetuplata.source.crypto.CryptoYield;
import ar.rindetuplata.source.macro.BcraMonetariasClient;
import ar.rindetuplata.source.macro.MacroObservation;
import ar.rindetuplata.source.macro.MacroVariable;
import ar.rindetuplata.source.plazofijo.ArgentinaDatosPlazoFijoAdapter;
import ar.rindetuplata.source.plazofijo.BankPlazoFijoRate;
import ar.rindetuplata.store.BankPlazoFijoWriter;
import ar.rindetuplata.store.CryptoYieldWriter;
import ar.rindetuplata.store.InstrumentKind;
import ar.rindetuplata.store.MacroObservationWriter;
import ar.rindetuplata.store.RunMode;
import ar.rindetuplata.store.RunStatus;
import ar.rindetuplata.store.SourceRunWriter;
import ar.rindetuplata.store.StoreReader;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared setup of the MCP tests: the whole application on a real port over a Testcontainers
 * Postgres and the frozen 2026-10-20 clock of the {@code api} tests. Requests of the server threads cannot see the
 * rolled-back transaction of a test, so the data is committed once per class by idempotent writers and is only ever
 * read. The rate limit is lifted here (every request comes from 127.0.0.1); {@code McpRateLimitApiTest} owns the
 * context that keeps it low.
 *
 * <p>The data: one bank captured on 2026-10-13 and 2026-10-14, one crypto yield, the BCRA REM and INDEC series, and
 * September 2026 with one wallet captured every day from 08-29 to 09-30, enough for one monthly report row.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.outbox.relay-enabled=false",
		"rindetuplata.outbox.consumer-enabled=false",
		"rindetuplata.api.rate-limit.capacity=100000"
})
@AutoConfigureMockMvc
@Import(ApiTestConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class McpIntegrationTest {

	static final String BANK = "Banco Mcp";
	static final LocalDate CAPTURE = LocalDate.parse("2026-10-14");
	static final Instant FETCHED = Instant.parse("2026-10-14T19:06:23Z");
	static final String WALLET_KEY = "mcp-alpha";

	static final JsonMapper JSON = JsonMapper.builder().build();

	@LocalServerPort
	protected int port;

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected JdbcClient jdbc;

	@Autowired
	protected StoreReader reader;

	@Autowired
	private BankPlazoFijoWriter bankWriter;

	@Autowired
	private CryptoYieldWriter cryptoWriter;

	@Autowired
	private MacroObservationWriter macroWriter;

	@Autowired
	private SourceRunWriter sourceRunWriter;

	protected long bankId;

	protected long walletId;

	@BeforeAll
	void seed() {
		LocalDate before = CAPTURE.minusDays(1);
		bankWriter.write(List.of(bank("0.39", "0.35", before)), before);
		bankWriter.write(List.of(bank("0.40", "0.36", CAPTURE)), CAPTURE);
		cryptoWriter.write(List.of(new CryptoYield(ArgentinaDatosCryptoAdapter.SOURCE, "Crypto Mcp", "ARS",
				new BigDecimal("0.08"), CAPTURE, FETCHED, Confidence.AGGREGATOR_AUTOMATIC, List.of())), CAPTURE);
		macroWriter.write(List.of(new MacroObservation(BcraMonetariasClient.SOURCE,
				MacroVariable.EXPECTED_INFLATION_12M, CAPTURE, new BigDecimal("0.25"), FETCHED,
				Confidence.OFFICIAL_AUTOMATIC, List.of())), CAPTURE);
		macroWriter.write(BcraMonetariasClient.parse(Fixtures.bytes("bcra/monetarias-27.json"),
				MacroVariable.MONTHLY_INFLATION, FETCHED), CAPTURE);
		bankId = reader.instrument(InstrumentKind.PLAZO_FIJO_BANK, ArgentinaDatosPlazoFijoAdapter.SOURCE.id(), BANK)
				.orElseThrow().id();
		seedSeptemberWallet();
		sourceRunWriter.record(CAPTURE, "mcp-test-source", RunMode.LIVE, RunStatus.OK, 1, null,
				Instant.parse("2026-10-14T19:30:00Z"));
	}

	private static BankPlazoFijoRate bank(String clientTna, String nonClientTna, LocalDate capture) {
		return new BankPlazoFijoRate(ArgentinaDatosPlazoFijoAdapter.SOURCE, BANK, new BigDecimal(clientTna),
				new BigDecimal(nonClientTna), List.of(), capture, FETCHED, Confidence.AGGREGATOR_AUTOMATIC, List.of());
	}

	/** A wallet at a constant 36.5% TNA captured on every day of the range, so September 2026 can be measured. */
	private void seedSeptemberWallet() {
		walletId = jdbc.sql("""
				insert into instrument (kind, source_id, natural_key, display_name, entity_name, segment, first_seen,
				                        last_seen)
				values ('WALLET', 'uala-cuenta-remunerada', :key, 'Alpha Wallet', 'Alpha Entity', 'SAVER',
				        date '2026-08-29', date '2026-09-30')
				on conflict (kind, source_id, natural_key) do update set last_seen = excluded.last_seen
				returning id
				""").param("key", WALLET_KEY).query(Long.class).single();
		jdbc.sql("""
				insert into rate_snapshot (instrument_id, capture_date, data_date, basis, rate, confidence, fetched_at,
				                           flags, invalid)
				select :id, day::date, day::date, 'TNA', 0.365, 'AGGREGATOR_AUTOMATIC', timestamptz '2026-10-14 19:06:23+00',
				       cast('[]' as jsonb), false
				from generate_series(date '2026-08-29', date '2026-09-30', interval '1 day') as day
				on conflict do nothing
				""").param("id", walletId).update();
	}

	protected McpSyncClient client() {
		HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
				.builder("http://127.0.0.1:" + port).endpoint("/mcp").build();
		return McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build();
	}

	protected CallToolResult call(McpSyncClient client, String tool, Map<String, Object> arguments) {
		return client.callTool(new CallToolRequest(tool, arguments));
	}

	protected static String text(CallToolResult result) {
		return ((TextContent) result.content().get(0)).text();
	}

	protected static JsonNode structured(CallToolResult result) {
		return JSON.valueToTree(result.structuredContent());
	}
}
