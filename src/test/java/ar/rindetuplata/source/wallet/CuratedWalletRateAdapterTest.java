package ar.rindetuplata.source.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.SourceException;
import ar.rindetuplata.source.ValidationRules;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
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
 * T6b rules for the curated wallet rates of S12: V4 seeds the Naranja X row the classpath seed used to hold and the
 * adapter reads it from the table with the same {@code WalletRate} output, the same confidence and the same 7-day
 * STALE rule. The pure document parser stays, because it is the shape V4 seeds and the admin API accepts.
 */
@Import(CuratedWalletTestConfiguration.class)
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *"
})
@Transactional
class CuratedWalletRateAdapterTest {

	/** 2026-10-14T12:00Z, the day after the seed's verifiedAt, so the row is fresh. */
	private static final Instant FETCHED_AT = Instant.parse("2026-10-14T12:00:00Z");

	@Autowired
	JdbcClient jdbc;

	@Test
	void v4SeedHoldsNaranjaXWithTheOwnerVerifiedRate() {
		List<WalletRate> rates = fetchAt(FETCHED_AT);

		assertThat(rates).hasSize(1);
		WalletRate naranja = rates.get(0);
		assertThat(naranja.source()).isEqualTo(CuratedWalletRateAdapter.SOURCE);
		assertThat(naranja.walletId()).isEqualTo("naranja-x");
		assertThat(naranja.name()).isEqualTo("Naranja X");
		assertThat(naranja.tiers()).singleElement().satisfies(tier -> {
			assertThat(tier.floor()).isNull();
			assertThat(tier.ceiling()).isEqualByComparingTo("1000000");
			assertThat(tier.tna()).isEqualByComparingTo("0.20");
		});
		assertThat(naranja.conditions()).isEmpty();
		assertThat(naranja.verifiedAt()).isEqualTo(LocalDate.parse("2026-10-13"));
		assertThat(naranja.dataDate()).isEqualTo(LocalDate.parse("2026-10-13"));
		assertThat(naranja.confidence()).isEqualTo(Confidence.CURATED_MANUAL);
		assertThat(naranja.source().official()).isFalse();
		assertThat(naranja.fetchedAt()).isEqualTo(FETCHED_AT);
		assertThat(naranja.flags()).isEmpty();
	}

	@Test
	void verificationOlderThanSevenDaysIsAStaleWarning() {
		WalletRate onTheLimit = fetchAt(Instant.parse("2026-10-20T12:00:00Z")).get(0);
		WalletRate stale = fetchAt(Instant.parse("2026-10-21T12:00:00Z")).get(0);

		assertThat(onTheLimit.flags()).isEmpty();
		assertThat(stale.flags()).singleElement().satisfies(f -> {
			assertThat(f.code()).isEqualTo(FlagCode.STALE);
			assertThat(f.severity()).isEqualTo(Severity.WARNING);
		});
		assertThat(stale.isInvalid()).isFalse();
	}

	@Test
	void aWalletDocumentParsesTiersAndConditionsOfAGenericEntry() {
		String json = """
				{"wallets":[{"id":"w","name":"W","verifiedAt":"2026-10-14",
				  "tiers":[{"ceiling":500000,"tnaPercent":30},{"ceiling":null,"tnaPercent":10.5}],
				  "conditions":[{"condition":"Si consumís $1","tiers":[{"ceiling":null,"tnaPercent":40}]}]}]}
				""";

		WalletRate rate = CuratedWalletRateAdapter
				.parse(json.getBytes(StandardCharsets.UTF_8), FETCHED_AT).get(0);

		assertThat(rate.tiers()).extracting(WalletTier::tna)
				.usingElementComparator(java.math.BigDecimal::compareTo)
				.containsExactly(new java.math.BigDecimal("0.3"), new java.math.BigDecimal("0.105"));
		assertThat(rate.tiers().get(1).ceiling()).isNull();
		assertThat(rate.conditions()).singleElement().satisfies(c -> {
			assertThat(c.condition()).isEqualTo("Si consumís $1");
			assertThat(c.tiers().get(0).tna()).isEqualByComparingTo("0.4");
		});
	}

	@Test
	void aMalformedOrUndatedDocumentIsASourceException() {
		String undated = """
				{"wallets":[{"id":"w","name":"W","tiers":[{"ceiling":1,"tnaPercent":5}]}]}
				""";

		assertThatThrownBy(() -> CuratedWalletRateAdapter.parse("{}".getBytes(StandardCharsets.UTF_8), FETCHED_AT))
				.isInstanceOf(SourceException.class);
		assertThatThrownBy(() -> CuratedWalletRateAdapter.parse("nope".getBytes(StandardCharsets.UTF_8), FETCHED_AT))
				.isInstanceOf(SourceException.class);
		assertThatThrownBy(() -> CuratedWalletRateAdapter.parse(undated.getBytes(StandardCharsets.UTF_8), FETCHED_AT))
				.isInstanceOf(SourceException.class).hasMessageContaining("verifiedAt");
	}

	private List<WalletRate> fetchAt(Instant fetchedAt) {
		return new CuratedWalletRateAdapter(jdbc, Clock.fixed(fetchedAt, ValidationRules.ARGENTINA)).fetch();
	}
}
