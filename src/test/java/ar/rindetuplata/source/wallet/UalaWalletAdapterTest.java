package ar.rindetuplata.source.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Fixtures;
import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.SourceException;
import ar.rindetuplata.source.SourceRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class UalaWalletAdapterTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-10-14T12:00:00Z");

    private final WalletRate fromExtract =
            UalaWalletAdapter.parseExtract(Fixtures.bytes("uala/cuenta-remunerada-extract.json"), FETCHED_AT);

    @Test
    void extractYieldsTheUnconditionalBaseRateAsATierWithItsBalanceRange() {
        assertThat(fromExtract.walletId()).isEqualTo("uala");
        assertThat(fromExtract.name()).isEqualTo("Ualá");
        assertThat(fromExtract.tiers()).singleElement().satisfies(tier -> {
            assertThat(tier.floor()).isEqualByComparingTo("10000");
            assertThat(tier.ceiling()).isEqualByComparingTo("1000000");
            assertThat(tier.tna()).isEqualByComparingTo("0.19");
        });
    }

    @Test
    void extractYieldsTheConditionalRatesAsLabeledConditions() {
        assertThat(fromExtract.conditions()).hasSize(2);
        assertThat(fromExtract.conditions().get(0).condition())
                .isEqualTo("Si acumulás $50.000 en ciertas operaciones este mes");
        assertThat(fromExtract.conditions().get(0).tiers()).singleElement()
                .satisfies(t -> assertThat(t.tna()).isEqualByComparingTo("0.20"));
        assertThat(fromExtract.conditions().get(1).condition()).isEqualTo("Si sumás $500.000");
        assertThat(fromExtract.conditions().get(1).tiers()).singleElement().satisfies(t -> {
            assertThat(t.tna()).isEqualByComparingTo("0.24");
            assertThat(t.floor()).isNull();
            assertThat(t.ceiling()).isNull();
        });
    }

    @Test
    void headlineSnippetsAreNotTakenAsRates() {
        assertThat(fromExtract.tiers()).hasSize(1);
        assertThat(fromExtract.conditions()).extracting(WalletCondition::condition)
                .noneMatch(c -> c.contains("hasta 24% TNA!"));
    }

    @Test
    void isScrapedFromTheEntityWebAndNotOfficial() {
        assertThat(fromExtract.confidence()).isEqualTo(Confidence.SCRAPED_ENTITY_WEB);
        assertThat(fromExtract.source().official()).isFalse();
        assertThat(fromExtract.fetchedAt()).isEqualTo(FETCHED_AT);
        assertThat(fromExtract.dataDate()).isEqualTo(LocalDate.parse("2026-10-14"));
        assertThat(fromExtract.verifiedAt()).isNull();
        assertThat(fromExtract.flags()).isEmpty();
    }

    @Test
    void htmlParsingGivesTheSameWalletAsTheArchivedExtract() {
        WalletRate fromHtml = UalaWalletAdapter.parseHtml(Fixtures.bytes("uala/cuenta-remunerada.html"), FETCHED_AT);

        assertThat(fromHtml).isEqualTo(fromExtract);
    }

    @Test
    void extractWithoutTheBaseRateSnippetIsASourceException() {
        String json = "{\"matches\":[\"Tu cuenta remunerada rinde hasta 24% TNA\"]}";

        assertThatThrownBy(() -> UalaWalletAdapter.parseExtract(json.getBytes(StandardCharsets.UTF_8), FETCHED_AT))
                .isInstanceOf(SourceException.class).hasMessageContaining("base rate");
    }

    @Test
    void absurdRatesAreFlaggedInvalid() {
        String json = "{\"matches\":[\"Si tenés entre $10.000 y $1.000.000 en tu cuenta rinden 400% TNA sin hacer nada.\"]}";

        WalletRate rate = UalaWalletAdapter.parseExtract(json.getBytes(StandardCharsets.UTF_8), FETCHED_AT);

        assertThat(rate.flags()).extracting(f -> f.code()).containsExactly(FlagCode.RATE_ABOVE_MAXIMUM);
        assertThat(rate.isInvalid()).isTrue();
    }

    @Test
    void malformedExtractsAndPagesAreSourceExceptions() {
        assertThatThrownBy(() -> UalaWalletAdapter.parseExtract("{}".getBytes(StandardCharsets.UTF_8), FETCHED_AT))
                .isInstanceOf(SourceException.class);
        assertThatThrownBy(() -> UalaWalletAdapter.parseExtract("zzz".getBytes(StandardCharsets.UTF_8), FETCHED_AT))
                .isInstanceOf(SourceException.class);
        assertThatThrownBy(() -> UalaWalletAdapter.parseHtml("<p>nothing</p>".getBytes(StandardCharsets.UTF_8), FETCHED_AT))
                .isInstanceOf(SourceException.class);
    }

    private UalaWalletAdapter adapter(RestClient.Builder builder) {
        return new UalaWalletAdapter(builder.build(), Clock.fixed(FETCHED_AT, ZoneOffset.UTC));
    }

    @Test
    void fetchGetsThePageAndParsesIt() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://uala.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://uala.test/inversiones/cuenta-remunerada"))
                .andRespond(withSuccess(Fixtures.bytes("uala/cuenta-remunerada.html"), MediaType.TEXT_HTML));

        List<WalletRate> rates = adapter(builder).fetch();

        assertThat(rates).containsExactly(fromExtract);
        server.verify();
    }

    @Test
    void fetchSurfacesBlockedAndBrokenResponses() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://uala.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://uala.test/inversiones/cuenta-remunerada")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo("https://uala.test/inversiones/cuenta-remunerada")).andRespond(withServerError());
        server.expect(requestTo("https://uala.test/inversiones/cuenta-remunerada"))
                .andRespond(withSuccess("<html>maintenance</html>", MediaType.TEXT_HTML));
        UalaWalletAdapter adapter = adapter(builder);

        assertThatThrownBy(adapter::fetch).isInstanceOfSatisfying(SourceRequestException.class,
                e -> assertThat(e.status()).isEqualTo(403));
        assertThatThrownBy(adapter::fetch).isInstanceOfSatisfying(SourceRequestException.class,
                e -> assertThat(e.status()).isEqualTo(500));
        assertThatThrownBy(adapter::fetch).isInstanceOf(SourceException.class);
    }
}
