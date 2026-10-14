package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.account.AccountRepository;
import ar.rindetuplata.account.AccountTestConfiguration;
import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.crypto.CryptoYield;
import ar.rindetuplata.store.CryptoYieldWriter;
import ar.rindetuplata.store.InstrumentKind;
import ar.rindetuplata.store.StoreReader;
import ar.rindetuplata.store.WalletRateWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * T6c rules of the alert evaluation (S3, S4): the day's ranking is compared against every active rule and the
 * crossing is edge-triggered. An entry above the threshold but not yet matched inserts one {@code alert_match} and
 * one {@code AlertTriggered} outbox row with the fields T6d's email needs; the same date evaluated again adds
 * nothing; an entry that drops below deletes its match and rising again later fires a new event; the rule's kind
 * and instrument narrow what can match; an inactive rule is ignored.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.auth.magic-link-url=https://app.rindetuplata.ar/ingresar"
})
@Import(AccountTestConfiguration.class)
@Transactional
class AlertEvaluationIntegrationTest {

	private static final LocalDate DAY_1 = LocalDate.parse("2026-10-14");
	private static final LocalDate DAY_2 = LocalDate.parse("2026-10-15");
	private static final LocalDate DAY_3 = LocalDate.parse("2026-10-16");
	private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");

	@Autowired
	AlertEvaluator evaluator;

	@Autowired
	AlertService alerts;

	@Autowired
	AccountRepository accounts;

	@Autowired
	WalletRateWriter walletWriter;

	@Autowired
	CryptoYieldWriter cryptoWriter;

	@Autowired
	StoreReader reader;

	@Autowired
	JdbcClient jdbc;

	@Test
	void anEntryAboveTheThresholdMatchesOnceAndEnqueuesOneEvent() {
		long wallet = seedWallet("test-eval-a", "wallet-a", "Wallet A", "0.30", DAY_1);
		String email = "eval-a@example.com";
		long rule = rule(email, "WALLET", wallet, "25");

		evaluator.evaluate(DAY_1);

		assertThat(matchInstrument(rule)).isEqualTo(wallet);
		assertThat(matchCount(rule)).isEqualTo(1);
		assertThat(matchedOn(rule)).isEqualTo(DAY_1);
		assertThat(outboxCount(rule)).isEqualTo(1);

		String payload = payload(rule);
		assertThat(field(payload, "$.userEmail")).isEqualTo(email);
		assertThat(number(payload, "$.ruleId")).isEqualTo(rule);
		assertThat(field(payload, "$.instrumentId")).isEqualTo(String.valueOf(wallet));
		assertThat(field(payload, "$.instrumentName")).isEqualTo("Wallet A");
		assertThat(field(payload, "$.kind")).isEqualTo("WALLET");
		assertThat(decimal(payload, "$.rate")).isEqualByComparingTo("0.30");
		assertThat(decimal(payload, "$.threshold")).isEqualByComparingTo("0.25");
		assertThat(field(payload, "$.captureDate")).isEqualTo(DAY_1.toString());
		assertThat(field(payload, "$.dataDate")).isEqualTo(DAY_1.toString());
		assertThat(field(payload, "$.sourceDisplayName")).isEqualTo("test-eval-a");
		assertThat(field(payload, "$.eventId")).isEqualTo(outboxId(rule));
	}

	@Test
	void evaluatingTheSameDateTwiceAddsNoNewEvents() {
		long wallet = seedWallet("test-eval-b", "wallet-b", "Wallet B", "0.30", DAY_1);
		long rule = rule("eval-b@example.com", null, null, "25");

		evaluator.evaluate(DAY_1);
		evaluator.evaluate(DAY_1);

		assertThat(wallet).isPositive();
		assertThat(matchCount(rule)).isEqualTo(1);
		assertThat(outboxCount(rule)).isEqualTo(1);
	}

	@Test
	void droppingBelowDeletesTheMatchAndRisingAgainFiresANewEvent() {
		long wallet = seedWallet("test-eval-c", "wallet-c", "Wallet C", "0.30", DAY_1);
		long rule = rule("eval-c@example.com", null, null, "25");

		evaluator.evaluate(DAY_1);
		assertThat(matchCount(rule)).isEqualTo(1);

		seedWallet("test-eval-c", "wallet-c", "Wallet C", "0.10", DAY_2);
		evaluator.evaluate(DAY_2);
		assertThat(wallet).isPositive();
		assertThat(matchCount(rule)).as("no longer above: the match is deleted, so the rule re-arms").isZero();
		assertThat(outboxCount(rule)).isEqualTo(1);

		seedWallet("test-eval-c", "wallet-c", "Wallet C", "0.30", DAY_3);
		evaluator.evaluate(DAY_3);

		assertThat(matchCount(rule)).isEqualTo(1);
		assertThat(matchedOn(rule)).as("the new edge has its own date").isEqualTo(DAY_3);
		assertThat(outboxCount(rule)).isEqualTo(2);
		assertThat(payloads(rule)).anySatisfy(payload -> assertThat(field(payload, "$.captureDate"))
			.isEqualTo(DAY_3.toString()));
	}

	@Test
	void theKindFilterOnlyLetsTheRulesKindMatch() {
		seedWallet("test-eval-d", "wallet-d", "Wallet D", "0.30", DAY_1);
		long crypto = seedCrypto("test-eval-d", "platform-d", "0.20", DAY_1);
		long rule = rule("eval-d@example.com", "CRYPTO", null, "10");

		evaluator.evaluate(DAY_1);

		assertThat(matchCount(rule)).as("the 30% wallet is not CRYPTO").isEqualTo(1);
		assertThat(matchInstrument(rule)).isEqualTo(crypto);
	}

	@Test
	void theInstrumentFilterOnlyLetsThatInstrumentMatch() {
		seedWallet("test-eval-e", "wallet-low", "Wallet Low", "0.30", DAY_1);
		long chosen = seedWallet("test-eval-e", "wallet-chosen", "Wallet Chosen", "0.28", DAY_1);
		long rule = rule("eval-e@example.com", null, chosen, "25");

		evaluator.evaluate(DAY_1);

		assertThat(matchCount(rule)).as("one rule, one instrument").isEqualTo(1);
		assertThat(matchInstrument(rule)).isEqualTo(chosen);
	}

	@Test
	void anInactiveRuleIsIgnored() {
		long wallet = seedWallet("test-eval-f", "wallet-f", "Wallet F", "0.30", DAY_1);
		long rule = rule("eval-f@example.com", "WALLET", wallet, "25");
		jdbc.sql("update alert_rule set active = false where id = :rule").param("rule", rule).update();

		evaluator.evaluate(DAY_1);

		assertThat(matchCount(rule)).isZero();
		assertThat(outboxCount(rule)).isZero();
	}

	/** Creates (or reuses) the account and a rule through the alert service, the same path the API uses. */
	private long rule(String email, String kind, Long instrumentId, String percent) {
		accounts.findByEmail(email).orElseGet(() -> accounts.create(email, NOW));
		return alerts.create(email, new AlertRequest(kind, instrumentId, new BigDecimal(percent))).id();
	}

	private long seedWallet(String sourceId, String walletId, String name, String tna, LocalDate captureDate) {
		ar.rindetuplata.source.wallet.WalletRate rate = new ar.rindetuplata.source.wallet.WalletRate(
				new SourceRef(sourceId, "Test " + sourceId, false), walletId, name,
				List.of(new ar.rindetuplata.source.wallet.WalletTier(BigDecimal.ZERO, null, new BigDecimal(tna))),
				List.of(), captureDate, null, NOW, Confidence.SCRAPED_ENTITY_WEB, List.of());
		walletWriter.write(List.of(rate), captureDate);
		return reader.instrument(InstrumentKind.WALLET, sourceId, walletId).orElseThrow().id();
	}

	private long seedCrypto(String sourceId, String platform, String apy, LocalDate captureDate) {
		CryptoYield yield = new CryptoYield(new SourceRef(sourceId, "Test " + sourceId, false), platform, "ARS",
				new BigDecimal(apy), captureDate, NOW, Confidence.AGGREGATOR_AUTOMATIC, List.of());
		cryptoWriter.write(List.of(yield), captureDate);
		return reader.instrument(InstrumentKind.CRYPTO_YIELD, sourceId, platform + "|ARS").orElseThrow().id();
	}

	private long matchCount(long rule) {
		return jdbc.sql("select count(*) from alert_match where rule_id = :rule")
			.param("rule", rule)
			.query(Long.class)
			.single();
	}

	private long matchInstrument(long rule) {
		return jdbc.sql("select instrument_id from alert_match where rule_id = :rule")
			.param("rule", rule)
			.query(Long.class)
			.single();
	}

	private LocalDate matchedOn(long rule) {
		return jdbc.sql("select matched_on from alert_match where rule_id = :rule")
			.param("rule", rule)
			.query(LocalDate.class)
			.single();
	}

	private long outboxCount(long rule) {
		return jdbc.sql("select count(*) from outbox_event where (payload->>'ruleId')::bigint = :rule")
			.param("rule", rule)
			.query(Long.class)
			.single();
	}

	private String outboxId(long rule) {
		return jdbc.sql("select id::text from outbox_event where (payload->>'ruleId')::bigint = :rule")
			.param("rule", rule)
			.query(String.class)
			.single();
	}

	private String payload(long rule) {
		return payloads(rule).get(0);
	}

	private List<String> payloads(long rule) {
		return jdbc.sql("select payload::text from outbox_event where (payload->>'ruleId')::bigint = :rule order by created_at")
			.param("rule", rule)
			.query(String.class)
			.list();
	}

	private static Object field(String payload, String path) {
		return com.jayway.jsonpath.JsonPath.read(payload, path);
	}

	private static long number(String payload, String path) {
		return ((Number) field(payload, path)).longValue();
	}

	private static BigDecimal decimal(String payload, String path) {
		return new BigDecimal(field(payload, path).toString());
	}

}
