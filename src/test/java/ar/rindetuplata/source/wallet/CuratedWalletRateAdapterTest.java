package ar.rindetuplata.source.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.SourceException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CuratedWalletRateAdapterTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-10-14T12:00:00Z");

    private static byte[] seed() throws IOException {
        return new ClassPathResource("curated-wallet-rates.json").getContentAsByteArray();
    }

    @Test
    void seedHoldsNaranjaXWithTheOwnerVerifiedRate() throws IOException {
        List<WalletRate> rates = CuratedWalletRateAdapter.parse(seed(), FETCHED_AT);

        assertThat(rates).hasSize(1);
        WalletRate naranja = rates.get(0);
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
    void verificationOlderThanSevenDaysIsAStaleWarning() throws IOException {
        WalletRate onTheLimit = CuratedWalletRateAdapter.parse(seed(), Instant.parse("2026-10-20T12:00:00Z")).get(0);
        WalletRate stale = CuratedWalletRateAdapter.parse(seed(), Instant.parse("2026-10-21T12:00:00Z")).get(0);

        assertThat(onTheLimit.flags()).isEmpty();
        assertThat(stale.flags()).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo(FlagCode.STALE);
            assertThat(f.severity()).isEqualTo(Severity.WARNING);
        });
        assertThat(stale.isInvalid()).isFalse();
    }

    @Test
    void parsesTiersAndConditionsOfAGenericEntry() {
        String json = """
                {"wallets":[{"id":"w","name":"W","verifiedAt":"2026-10-14",
                  "tiers":[{"ceiling":500000,"tnaPercent":30},{"ceiling":null,"tnaPercent":10.5}],
                  "conditions":[{"condition":"Si consumís $1","tiers":[{"ceiling":null,"tnaPercent":40}]}]}]}
                """;

        WalletRate rate = CuratedWalletRateAdapter.parse(json.getBytes(StandardCharsets.UTF_8), FETCHED_AT).get(0);

        assertThat(rate.tiers()).extracting(WalletTier::tna).usingElementComparator(java.math.BigDecimal::compareTo)
                .containsExactly(new java.math.BigDecimal("0.3"), new java.math.BigDecimal("0.105"));
        assertThat(rate.tiers().get(1).ceiling()).isNull();
        assertThat(rate.conditions()).singleElement().satisfies(c -> {
            assertThat(c.condition()).isEqualTo("Si consumís $1");
            assertThat(c.tiers().get(0).tna()).isEqualByComparingTo("0.4");
        });
    }

    @Test
    void zeroRateIsInvalidAndMissingVerificationDateIsASourceException() {
        String zero = """
                {"wallets":[{"id":"w","name":"W","verifiedAt":"2026-10-14","tiers":[{"ceiling":1,"tnaPercent":0}]}]}
                """;
        String undated = """
                {"wallets":[{"id":"w","name":"W","tiers":[{"ceiling":1,"tnaPercent":5}]}]}
                """;

        assertThat(CuratedWalletRateAdapter.parse(zero.getBytes(StandardCharsets.UTF_8), FETCHED_AT).get(0).isInvalid())
                .isTrue();
        assertThatThrownBy(() -> CuratedWalletRateAdapter.parse(undated.getBytes(StandardCharsets.UTF_8), FETCHED_AT))
                .isInstanceOf(SourceException.class).hasMessageContaining("verifiedAt");
    }

    @Test
    void malformedSeedIsASourceException() {
        assertThatThrownBy(() -> CuratedWalletRateAdapter.parse("{}".getBytes(StandardCharsets.UTF_8), FETCHED_AT))
                .isInstanceOf(SourceException.class);
        assertThatThrownBy(() -> CuratedWalletRateAdapter.parse("nope".getBytes(StandardCharsets.UTF_8), FETCHED_AT))
                .isInstanceOf(SourceException.class);
    }

    @Test
    void fetchReadsTheClasspathSeed() {
        CuratedWalletRateAdapter adapter = new CuratedWalletRateAdapter(new ClassPathResource("curated-wallet-rates.json"),
                Clock.fixed(FETCHED_AT, ZoneOffset.UTC));

        assertThat(adapter.fetch()).extracting(WalletRate::walletId).containsExactly("naranja-x");
    }

    @Test
    void fetchFailsClearlyWhenTheSeedIsMissing() {
        CuratedWalletRateAdapter adapter = new CuratedWalletRateAdapter(new ClassPathResource("does-not-exist.json"),
                Clock.fixed(FETCHED_AT, ZoneOffset.UTC));

        assertThatThrownBy(adapter::fetch).isInstanceOf(SourceException.class);
    }
}
