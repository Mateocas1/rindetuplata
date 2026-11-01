package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.account.AccountRepository;
import ar.rindetuplata.account.AccountTestConfiguration;
import ar.rindetuplata.ranking.RankedEntry;
import ar.rindetuplata.ranking.RankedKind;
import ar.rindetuplata.ranking.RankingQuery;
import ar.rindetuplata.ranking.RankingService;
import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.wallet.WalletRate;
import ar.rindetuplata.source.wallet.WalletTier;
import ar.rindetuplata.store.InstrumentKind;
import ar.rindetuplata.store.StoreReader;
import ar.rindetuplata.store.WalletRateWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * T1b (S1): the alert evaluation sees every ranked entry, not only the public page. The live ranking holds hundreds
 * of instruments (398 on the recorded day) while the public API caps a page at {@link RankingQuery#MAX_LIMIT}, so
 * a rule on an instrument ranked beyond that position must still fire, and a match on such an instrument that is
 * still above the threshold must not be deleted as if it had dropped out of the ranking. The test seeds
 * {@value #CROWD} wallets that all outrank the target, so the target sits past the public page.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.auth.magic-link-url=https://app.rindetuplata.ar/ingresar"
})
@Import(AccountTestConfiguration.class)
@Transactional
class AlertEvaluationBeyondPageIntegrationTest {

	private static final LocalDate DAY = LocalDate.parse("2026-10-14");

	private static final int CROWD = RankingQuery.MAX_LIMIT + 10;

	private static final String SOURCE = "test-beyond";

	private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");

	@Autowired
	WalletRateWriter walletWriter;

	@Autowired
	StoreReader reader;

	@Autowired
	AlertEvaluator evaluator;

	@Autowired
	AlertService alerts;

	@Autowired
	AccountRepository accounts;

	@Autowired
	RankingService ranking;

	@Autowired
	JdbcClient jdbc;

	@Test
	void aRuleOnAnInstrumentRankedBeyondThePublicPageFiresOnceAndEnqueuesOneEvent() {
		String beyond = beyondThePublicPage();
		long rule = rule("beyond-fires@example.com", beyond, "0.001");

		evaluator.evaluate(DAY);
		evaluator.evaluate(DAY);

		assertThat(matchedInstruments(rule)).containsExactly(Long.valueOf(beyond));
		assertThat(outboxCount(rule)).isEqualTo(1);
	}

	@Test
	void aMatchOnAnInstrumentBeyondThePublicPageStillAboveTheThresholdIsKept() {
		String beyond = beyondThePublicPage();
		long rule = rule("beyond-kept@example.com", beyond, "0.001");
		jdbc.sql("insert into alert_match (rule_id, instrument_id, matched_on) values (:rule, :instrument, :day)")
			.param("rule", rule)
			.param("instrument", Long.valueOf(beyond))
			.param("day", DAY)
			.update();

		evaluator.evaluate(DAY);

		assertThat(matchedInstruments(rule)).as("still above: the match is not re-armed").containsExactly(Long.valueOf(beyond));
		assertThat(outboxCount(rule)).as("an existing match enqueues nothing").isZero();
	}

	@Test
	void aMatchOnAnInstrumentBeyondThePublicPageThatDropsBelowTheThresholdReArms() {
		String beyond = beyondThePublicPage();
		long rule = rule("beyond-rearms@example.com", beyond, "10");
		jdbc.sql("insert into alert_match (rule_id, instrument_id, matched_on) values (:rule, :instrument, :day)")
			.param("rule", rule)
			.param("instrument", Long.valueOf(beyond))
			.param("day", DAY)
			.update();

		evaluator.evaluate(DAY);

		assertThat(matchedInstruments(rule)).as("below the threshold: the rule re-arms").isEmpty();
		assertThat(outboxCount(rule)).isZero();
	}

	@Test
	void aTopRankedInstrumentStillFiresOnce() {
		beyondThePublicPage();
		RankedEntry top = ranking.rank(query(EnumSet.allOf(RankedKind.class))).ranked().get(0);
		long rule = rule("top-fires@example.com", top.id(), "0.001");

		evaluator.evaluate(DAY);
		evaluator.evaluate(DAY);

		assertThat(matchedInstruments(rule)).containsExactly(Long.valueOf(top.id()));
		assertThat(outboxCount(rule)).isEqualTo(1);
	}

	/**
	 * The target wallet: {@value #CROWD} other wallets pay more, so it is ranked after the public page, and it still
	 * pays 5% a year, above every threshold the tests use except the one meant to re-arm.
	 */
	private String beyondThePublicPage() {
		List<WalletRate> rates = new ArrayList<>();
		for (int i = 0; i < CROWD; i++) {
			rates.add(wallet("crowd-%03d".formatted(i), new BigDecimal("0.30").add(new BigDecimal(i).movePointLeft(4))));
		}
		rates.add(wallet("target", new BigDecimal("0.05")));
		walletWriter.write(rates, DAY);

		var all = ranking.rank(query(EnumSet.allOf(RankedKind.class)));
		assertThat(all.totals().ranked()).as("more wallets than one page").isGreaterThan(RankingQuery.MAX_LIMIT);
		String target = String.valueOf(reader.instrument(InstrumentKind.WALLET, SOURCE, "target").orElseThrow().id());
		assertThat(all.ranked()).as("the target is not on the public page").noneMatch(entry -> entry.id().equals(target));
		return target;
	}

	private WalletRate wallet(String walletId, BigDecimal tna) {
		return new WalletRate(new SourceRef(SOURCE, "Test " + SOURCE, false), walletId, "Wallet " + walletId,
				List.of(new WalletTier(BigDecimal.ZERO, null, tna)), List.of(), DAY, null, NOW,
				Confidence.SCRAPED_ENTITY_WEB, List.of());
	}

	private static RankingQuery query(Set<RankedKind> kinds) {
		return new RankingQuery(RankingQuery.DEFAULT_AMOUNT, RankingQuery.DEFAULT_DAYS, Set.of(), kinds, DAY,
				RankingQuery.MAX_LIMIT);
	}

	private long rule(String email, String instrumentId, String percent) {
		accounts.findByEmail(email).orElseGet(() -> accounts.create(email, NOW));
		return alerts.create(email, new AlertRequest(null, null, Long.valueOf(instrumentId), new BigDecimal(percent))).id();
	}

	private List<Long> matchedInstruments(long rule) {
		return jdbc.sql("select instrument_id from alert_match where rule_id = :rule order by instrument_id")
			.param("rule", rule)
			.query(Long.class)
			.list();
	}

	private long outboxCount(long rule) {
		return jdbc.sql("select count(*) from outbox_event where (payload->>'ruleId')::bigint = :rule")
			.param("rule", rule)
			.query(Long.class)
			.single();
	}

}
