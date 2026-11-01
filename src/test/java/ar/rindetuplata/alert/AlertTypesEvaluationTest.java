package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * T6c, L32 a: the evaluation of the three new rule types after a COMPLETED ingestion, through the real API and the
 * real ranking. Every case asserts the outbox rows it leaves (type, payload, how many) and the state it keeps for
 * the next evaluation, because both decide whether the saver is told once, twice or never.
 */
class AlertTypesEvaluationTest extends AlertApiIntegrationTest {

	/** A Wednesday; its ISO week starts on Monday 2026-10-12. */
	private static final LocalDate WED = LocalDate.parse("2026-10-14");
	private static final LocalDate THU = LocalDate.parse("2026-10-15");
	private static final LocalDate FRI = LocalDate.parse("2026-10-16");
	private static final LocalDate SUN = LocalDate.parse("2026-10-18");
	private static final LocalDate NEXT_MON = LocalDate.parse("2026-10-19");
	private static final LocalDate NEXT_TUE = LocalDate.parse("2026-10-20");

	@Autowired
	AlertEvaluator evaluator;

	@Autowired
	JdbcClient jdbc;

	// ---- BEST_CHANGED ------------------------------------------------------------------------------------------

	@Test
	void bestChangedStoresTheBaselineOnTheFirstEvaluationAndFiresNothing() throws Exception {
		long a = seedWallet("test-best-a", "wallet-a", "Wallet A", "0.30", WED);
		seedWallet("test-best-a", "wallet-b", "Wallet B", "0.20", WED);
		long rule = create("{\"type\":\"BEST_CHANGED\"}");

		evaluator.evaluate(WED);

		assertThat(events(rule)).as("the first evaluation only learns the top").isEmpty();
		assertThat(stateKey(rule)).isEqualTo("id:" + a);
		assertThat(stateName(rule)).isEqualTo("Wallet A");
	}

	@Test
	void bestChangedFiresOncePerChangeOfTheTopAndNamesBothOptions() throws Exception {
		long a = seedWallet("test-best-b", "wallet-a", "Wallet A", "0.30", WED);
		long b = seedWallet("test-best-b", "wallet-b", "Wallet B", "0.20", WED);
		long rule = create("{\"type\":\"BEST_CHANGED\"}");
		evaluator.evaluate(WED);

		seedWallet("test-best-b", "wallet-b", "Wallet B", "0.35", THU);
		evaluator.evaluate(THU);
		evaluator.evaluate(THU);

		assertThat(events(rule)).as("one change, one event, however many times the day is evaluated").hasSize(1);
		String payload = events(rule).get(0);
		assertThat(string(payload, "$.type")).isEqualTo("BEST_CHANGED");
		assertThat(string(payload, "$.userEmail")).isEqualTo(USER_EMAIL);
		assertThat(number(payload, "$.ruleId")).isEqualTo(rule);
		assertThat(string(payload, "$.instrumentId")).isEqualTo(String.valueOf(b));
		assertThat(string(payload, "$.instrumentName")).isEqualTo("Wallet B");
		assertThat(string(payload, "$.kind")).isEqualTo("WALLET");
		assertThat(decimal(payload, "$.rate")).isEqualByComparingTo("0.35");
		assertThat(string(payload, "$.captureDate")).isEqualTo(THU.toString());
		assertThat(string(payload, "$.dataDate")).isEqualTo(THU.toString());
		assertThat(string(payload, "$.sourceDisplayName")).isEqualTo("test-best-b");
		assertThat(string(payload, "$.previousName")).isEqualTo("Wallet A");
		assertThat(string(payload, "$.previousObservedOn")).isEqualTo(WED.toString());
		assertThat((Object) JsonPath.read(payload, "$.ruleKind")).isNull();
		assertThat(eventType(rule)).isEqualTo("BestChanged");
		assertThat(stateKey(rule)).as("the new top is the next baseline").isEqualTo("id:" + b);

		seedWallet("test-best-b", "wallet-a", "Wallet A", "0.40", FRI);
		evaluator.evaluate(FRI);

		assertThat(events(rule)).as("the old top coming back is a change too").hasSize(2);
		assertThat(string(events(rule).get(1), "$.instrumentId")).isEqualTo(String.valueOf(a));
		assertThat(string(events(rule).get(1), "$.previousName")).isEqualTo("Wallet B");
	}

	@Test
	void bestChangedWithAKindOnlyWatchesThatKind() throws Exception {
		seedWallet("test-best-c", "wallet-a", "Wallet A", "0.60", WED);
		long x = seedCrypto("test-best-c", "platform-x", "0.20", WED);
		seedCrypto("test-best-c", "platform-y", "0.10", WED);
		long rule = create("{\"type\":\"BEST_CHANGED\",\"kind\":\"CRYPTO\"}");
		evaluator.evaluate(WED);
		assertThat(stateKey(rule)).as("the top of CRYPTO, not of everything").isEqualTo("id:" + x);

		seedWallet("test-best-c", "wallet-b", "Wallet B", "0.90", THU);
		evaluator.evaluate(THU);
		assertThat(events(rule)).as("a new top among wallets is not the crypto top").isEmpty();

		long y = seedCrypto("test-best-c", "platform-y", "0.50", FRI);
		evaluator.evaluate(FRI);
		assertThat(events(rule)).hasSize(1);
		assertThat(string(events(rule).get(0), "$.instrumentId")).isEqualTo(String.valueOf(y));
		assertThat(string(events(rule).get(0), "$.ruleKind")).isEqualTo("CRYPTO");
	}

	@Test
	void bestChangedOverAnEmptyFilterChangesNothing() throws Exception {
		seedWallet("test-best-d", "wallet-a", "Wallet A", "0.30", WED);
		long rule = create("{\"type\":\"BEST_CHANGED\",\"kind\":\"FUND\"}");

		evaluator.evaluate(WED);

		assertThat(events(rule)).isEmpty();
		assertThat(stateRows(rule)).as("no top, nothing to remember").isZero();
	}

	// ---- RATE_DROP ---------------------------------------------------------------------------------------------

	@Test
	void rateDropStoresTheBaselineThenFiresOnEveryStrictDropOnce() throws Exception {
		long wallet = seedWallet("test-drop-a", "wallet-a", "Wallet A", "0.30", WED);
		long rule = create("{\"type\":\"RATE_DROP\",\"instrumentId\":%d}".formatted(wallet));

		evaluator.evaluate(WED);
		assertThat(events(rule)).as("the first evaluation only stores the rate").isEmpty();
		assertThat(stateRate(rule)).isEqualByComparingTo("0.30");

		evaluator.evaluate(THU);
		assertThat(events(rule)).as("the same rate is not a drop").isEmpty();

		seedWallet("test-drop-a", "wallet-a", "Wallet A", "0.28", THU);
		evaluator.evaluate(THU);
		evaluator.evaluate(THU);
		assertThat(events(rule)).as("a drop fires once, however many times the day is evaluated").hasSize(1);
		String payload = events(rule).get(0);
		assertThat(string(payload, "$.type")).isEqualTo("RATE_DROP");
		assertThat(string(payload, "$.userEmail")).isEqualTo(USER_EMAIL);
		assertThat(number(payload, "$.ruleId")).isEqualTo(rule);
		assertThat(string(payload, "$.instrumentId")).isEqualTo(String.valueOf(wallet));
		assertThat(string(payload, "$.instrumentName")).isEqualTo("Wallet A");
		assertThat(string(payload, "$.kind")).isEqualTo("WALLET");
		assertThat(decimal(payload, "$.previousRate")).isEqualByComparingTo("0.30");
		assertThat(decimal(payload, "$.rate")).isEqualByComparingTo("0.28");
		assertThat(string(payload, "$.previousObservedOn")).as("the last evaluation that saw the old rate")
			.isEqualTo(THU.toString());
		assertThat(string(payload, "$.captureDate")).isEqualTo(THU.toString());
		assertThat(string(payload, "$.dataDate")).isEqualTo(THU.toString());
		assertThat(string(payload, "$.sourceDisplayName")).isEqualTo("test-drop-a");
		assertThat(eventType(rule)).isEqualTo("RateDropped");
		assertThat(stateRate(rule)).as("the stored rate moved to today's").isEqualByComparingTo("0.28");

		seedWallet("test-drop-a", "wallet-a", "Wallet A", "0.29", FRI);
		evaluator.evaluate(FRI);
		assertThat(events(rule)).as("a rise is not a drop").hasSize(1);
		assertThat(stateRate(rule)).as("the stored rate follows a rise too").isEqualByComparingTo("0.29");

		seedWallet("test-drop-a", "wallet-a", "Wallet A", "0.27", SUN);
		evaluator.evaluate(SUN);
		assertThat(events(rule)).as("a new drop below the moved rate fires again").hasSize(2);
		assertThat(decimal(events(rule).get(1), "$.previousRate")).isEqualByComparingTo("0.29");
	}

	@Test
	void anUnrankedDayChangesNothingForRateDrop() throws Exception {
		long wallet = seedWallet("test-drop-b", "wallet-a", "Wallet A", "0.30", WED);
		long rule = create("{\"type\":\"RATE_DROP\",\"instrumentId\":%d}".formatted(wallet));
		evaluator.evaluate(WED);

		evaluator.evaluate(WED.minusDays(10));

		assertThat(events(rule)).isEmpty();
		assertThat(stateRate(rule)).as("no ranking row, the stored rate stays").isEqualByComparingTo("0.30");
		assertThat(stateDate(rule)).isEqualTo(WED);
	}

	@Test
	void anotherInstrumentDroppingDoesNotFireRateDrop() throws Exception {
		long watched = seedWallet("test-drop-c", "wallet-a", "Wallet A", "0.30", WED);
		seedWallet("test-drop-c", "wallet-b", "Wallet B", "0.30", WED);
		long rule = create("{\"type\":\"RATE_DROP\",\"instrumentId\":%d}".formatted(watched));
		evaluator.evaluate(WED);

		seedWallet("test-drop-c", "wallet-b", "Wallet B", "0.10", THU);
		evaluator.evaluate(THU);

		assertThat(events(rule)).isEmpty();
	}

	// ---- WEEKLY_DIGEST -----------------------------------------------------------------------------------------

	@Test
	void theDigestListsTheTopFiveInRankingOrderAndIsSentOncePerWeek() throws Exception {
		for (int i = 1; i <= 6; i++) {
			seedWallet("test-digest-a", "wallet-" + i, "Wallet " + i, "0.3" + i, WED);
		}
		long rule = create("{\"type\":\"WEEKLY_DIGEST\"}");

		evaluator.evaluate(WED);
		evaluator.evaluate(WED);
		evaluator.evaluate(THU);
		evaluator.evaluate(FRI);

		assertThat(events(rule)).as("one digest for the week, however many evaluations").hasSize(1);
		String payload = events(rule).get(0);
		assertThat(string(payload, "$.type")).isEqualTo("WEEKLY_DIGEST");
		assertThat(string(payload, "$.userEmail")).isEqualTo(USER_EMAIL);
		assertThat(number(payload, "$.ruleId")).isEqualTo(rule);
		assertThat(string(payload, "$.weekStart")).as("the Monday of the data date's week").isEqualTo("2026-10-12");
		assertThat(string(payload, "$.dataDate")).isEqualTo(WED.toString());
		assertThat(JsonPath.<List<String>>read(payload, "$.entries[*].name"))
			.containsExactly("Wallet 6", "Wallet 5", "Wallet 4", "Wallet 3", "Wallet 2");
		assertThat(decimal(payload, "$.entries[0].rate")).isEqualByComparingTo("0.36");
		assertThat(string(payload, "$.entries[0].kind")).isEqualTo("WALLET");
		assertThat(string(payload, "$.entries[0].sourceDisplayName")).isEqualTo("test-digest-a");
		assertThat(string(payload, "$.entries[0].captureDate")).isEqualTo(WED.toString());
		assertThat(string(payload, "$.entries[0].dataDate")).isEqualTo(WED.toString());
		assertThat((Object) JsonPath.read(payload, "$.entries[0].realReturn"))
			.as("no REM observation is stored, so there is no real return to show").isNull();
		assertThat((Object) JsonPath.read(payload, "$.topChangedFrom")).as("no previous digest").isNull();
		assertThat(eventType(rule)).isEqualTo("WeeklyDigest");
		assertThat(digestWeeks(rule)).containsExactly(LocalDate.parse("2026-10-12"));
	}

	@Test
	void theDigestOfTheNextWeekComesOnMondayAndSaysWhenTheTopChanged() throws Exception {
		seedWallet("test-digest-b", "wallet-a", "Wallet A", "0.30", WED);
		long rule = create("{\"type\":\"WEEKLY_DIGEST\"}");
		evaluator.evaluate(SUN);
		assertThat(events(rule)).hasSize(1);
		assertThat(string(events(rule).get(0), "$.weekStart")).as("Sunday still belongs to the week of Monday 12")
			.isEqualTo("2026-10-12");

		seedWallet("test-digest-b", "wallet-b", "Wallet B", "0.33", NEXT_MON);
		evaluator.evaluate(NEXT_MON);
		evaluator.evaluate(NEXT_TUE);

		assertThat(events(rule)).hasSize(2);
		String second = events(rule).get(1);
		assertThat(string(second, "$.weekStart")).isEqualTo("2026-10-19");
		assertThat(string(second, "$.topChangedFrom.name")).isEqualTo("Wallet A");
		assertThat(string(second, "$.topChangedFrom.weekStart")).isEqualTo("2026-10-12");
		assertThat(digestWeeks(rule)).containsExactly(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-19"));

		seedWallet("test-digest-b", "wallet-b", "Wallet B", "0.34", LocalDate.parse("2026-10-26"));
		evaluator.evaluate(LocalDate.parse("2026-10-26"));
		assertThat(events(rule)).hasSize(3);
		assertThat((Object) JsonPath.read(events(rule).get(2), "$.topChangedFrom"))
			.as("the top is the same as the previous digest's").isNull();
	}

	@Test
	void theDigestWithAKindListsOnlyThatKind() throws Exception {
		seedWallet("test-digest-c", "wallet-a", "Wallet A", "0.60", WED);
		seedCrypto("test-digest-c", "platform-x", "0.20", WED);
		long rule = create("{\"type\":\"WEEKLY_DIGEST\",\"kind\":\"CRYPTO\"}");

		evaluator.evaluate(WED);

		assertThat(events(rule)).hasSize(1);
		assertThat(JsonPath.<List<String>>read(events(rule).get(0), "$.entries[*].kind")).containsExactly("CRYPTO");
		assertThat(string(events(rule).get(0), "$.ruleKind")).isEqualTo("CRYPTO");
	}

	@Test
	void aDigestWithNothingToListIsNeitherSentNorRecorded() throws Exception {
		seedWallet("test-digest-d", "wallet-a", "Wallet A", "0.30", WED);
		long rule = create("{\"type\":\"WEEKLY_DIGEST\",\"kind\":\"FUND\"}");

		evaluator.evaluate(WED);

		assertThat(events(rule)).isEmpty();
		assertThat(digestWeeks(rule)).as("the week stays free for a later evaluation").isEmpty();
	}

	@Test
	void theDigestKeyIsUniquePerRuleAndWeek() throws Exception {
		seedWallet("test-digest-e", "wallet-a", "Wallet A", "0.30", WED);
		long rule = create("{\"type\":\"WEEKLY_DIGEST\"}");
		evaluator.evaluate(WED);

		assertThatThrownBy(() -> jdbc.sql("""
				insert into alert_digest (rule_id, week_start, sent_for, top_key, top_name)
				values (:rule, date '2026-10-12', date '2026-10-14', 'id:1', 'Wallet')
				""").param("rule", rule).update()).isInstanceOf(DuplicateKeyException.class);
	}

	// ---- THRESHOLD is unchanged --------------------------------------------------------------------------------

	@Test
	void aThresholdRuleStillFiresTheLegacyEventShape() throws Exception {
		long wallet = seedWallet("test-legacy", "wallet-a", "Wallet A", "0.30", WED);
		long rule = create("{\"kind\":\"WALLET\",\"instrumentId\":%d,\"thresholdPercent\":25}".formatted(wallet));

		evaluator.evaluate(WED);

		assertThat(events(rule)).hasSize(1);
		String payload = events(rule).get(0);
		assertThat(JsonPath.<java.util.Map<String, Object>>read(payload, "$")).as("today's shape has no type")
			.doesNotContainKey("type");
		assertThat(eventType(rule)).isEqualTo("AlertTriggered");
		assertThat(string(payload, "$.instrumentName")).isEqualTo("Wallet A");
		assertThat(decimal(payload, "$.threshold")).isEqualByComparingTo("0.25");
		assertThat(jdbc.sql("select count(*) from alert_match where rule_id = :rule").param("rule", rule)
			.query(Long.class).single()).isEqualTo(1);
		assertThat(stateRows(rule)).as("THRESHOLD keeps its state in alert_match only").isZero();
	}

	// ---- helpers -----------------------------------------------------------------------------------------------

	private long create(String json) throws Exception {
		Cookie session = login(USER_EMAIL);
		String body = postAlert(session, json).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private List<String> events(long rule) {
		return jdbc.sql("select payload::text from outbox_event where (payload->>'ruleId')::bigint = :rule"
				+ " order by payload->>'dataDate', created_at")
			.param("rule", rule)
			.query(String.class)
			.list();
	}

	private String eventType(long rule) {
		return jdbc.sql("select distinct event_type from outbox_event where (payload->>'ruleId')::bigint = :rule")
			.param("rule", rule)
			.query(String.class)
			.single();
	}

	private String stateKey(long rule) {
		return jdbc.sql("select subject_key from alert_state where rule_id = :rule").param("rule", rule)
			.query(String.class).single();
	}

	private String stateName(long rule) {
		return jdbc.sql("select subject_name from alert_state where rule_id = :rule").param("rule", rule)
			.query(String.class).single();
	}

	private BigDecimal stateRate(long rule) {
		return jdbc.sql("select rate from alert_state where rule_id = :rule").param("rule", rule)
			.query(BigDecimal.class).single();
	}

	private LocalDate stateDate(long rule) {
		return jdbc.sql("select observed_on from alert_state where rule_id = :rule").param("rule", rule)
			.query(LocalDate.class).single();
	}

	private long stateRows(long rule) {
		return jdbc.sql("select count(*) from alert_state where rule_id = :rule").param("rule", rule)
			.query(Long.class).single();
	}

	private List<LocalDate> digestWeeks(long rule) {
		return jdbc.sql("select week_start from alert_digest where rule_id = :rule order by week_start")
			.param("rule", rule)
			.query(LocalDate.class)
			.list();
	}

	private static String string(String payload, String path) {
		return JsonPath.read(payload, path);
	}

	private static long number(String payload, String path) {
		return ((Number) JsonPath.read(payload, path)).longValue();
	}

	private static BigDecimal decimal(String payload, String path) {
		return new BigDecimal(JsonPath.read(payload, path).toString());
	}

}
