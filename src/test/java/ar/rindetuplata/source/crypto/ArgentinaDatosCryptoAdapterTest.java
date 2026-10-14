package ar.rindetuplata.source.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Fixtures;
import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.SourceException;
import ar.rindetuplata.source.ValidationFlag;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ArgentinaDatosCryptoAdapterTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-10-14T12:00:00Z");

    private final List<CryptoYield> yields =
            ArgentinaDatosCryptoAdapter.parse(Fixtures.bytes("argentinadatos/rendimientos.json"), FETCHED_AT);

    private CryptoYield findYield(String platform, String currency) {
        return yields.stream().filter(y -> y.platform().equals(platform) && y.currency().equals(currency))
                .findFirst().orElseThrow();
    }

    @Test
    void flattensEveryPlatformCurrencyPair() {
        assertThat(yields).hasSize(64);
        assertThat(yields).extracting(CryptoYield::platform).doesNotContain("decrypto");
    }

    @Test
    void convertsApyPercentToFraction() {
        assertThat(findYield("nexo", "USDT").apy()).isEqualByComparingTo("0.115");
        assertThat(findYield("nexo", "BTC").apy()).isEqualByComparingTo("0.062");
        assertThat(findYield("lemoncash", "USDT").apy()).isEqualByComparingTo("0.0226");
        assertThat(findYield("astropay", "USD").apy()).isEqualByComparingTo("0.06");
    }

    @Test
    void floatingPointNoiseInTheSourceIsRemoved() {
        assertThat(findYield("nexo", "BNB").apy()).isEqualByComparingTo("0.07");
    }

    @Test
    void carriesDateSourceAndConfidence() {
        CryptoYield nexo = findYield("nexo", "USDC");

        assertThat(nexo.date()).isEqualTo(LocalDate.parse("2026-10-14"));
        assertThat(nexo.confidence()).isEqualTo(Confidence.AGGREGATOR_AUTOMATIC);
        assertThat(nexo.source().official()).isFalse();
        assertThat(nexo.fetchedAt()).isEqualTo(FETCHED_AT);
        assertThat(nexo.flags()).isEmpty();
    }

    @Test
    void rowsOlderThanSevenDaysAreStaleWarnings() {
        List<CryptoYield> stale = yields.stream()
                .filter(y -> y.flags().stream().map(ValidationFlag::code).anyMatch(FlagCode.STALE::equals)).toList();

        assertThat(stale).hasSize(7).allSatisfy(y -> {
            assertThat(y.date()).isBefore(LocalDate.parse("2026-10-07"));
            assertThat(y.flags()).allMatch(f -> f.severity() == Severity.WARNING);
        });
        assertThat(yields).filteredOn(y -> y.date().equals(LocalDate.parse("2026-10-13")))
                .allSatisfy(y -> assertThat(y.flags()).isEmpty());
    }

    @Test
    void zeroNullAndAbsurdApyAreInvalid() {
        String json = """
                [{"entidad":"x","rendimientos":[
                  {"moneda":"A","apy":0,"fecha":"2026-10-14"},
                  {"moneda":"B","apy":null,"fecha":"2026-10-14"},
                  {"moneda":"C","apy":350,"fecha":"2026-10-14"}]}]
                """;

        List<CryptoYield> parsed = ArgentinaDatosCryptoAdapter.parse(json.getBytes(StandardCharsets.UTF_8), FETCHED_AT);

        assertThat(parsed).hasSize(3).allMatch(CryptoYield::isInvalid);
        assertThat(parsed.get(2).flags()).extracting(ValidationFlag::code).containsExactly(FlagCode.RATE_ABOVE_MAXIMUM);
    }

    @Test
    void malformedBodiesAreSourceExceptions() {
        assertThatThrownBy(() -> ArgentinaDatosCryptoAdapter.parse("[1]".getBytes(StandardCharsets.UTF_8), FETCHED_AT))
                .isInstanceOf(SourceException.class);
        assertThatThrownBy(() -> ArgentinaDatosCryptoAdapter.parse("nope".getBytes(StandardCharsets.UTF_8), FETCHED_AT))
                .isInstanceOf(SourceException.class);
    }

    @Test
    void fetchCallsTheRendimientosPath() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ad.test/v1/finanzas");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ad.test/v1/finanzas/rendimientos"))
                .andRespond(withSuccess(Fixtures.bytes("argentinadatos/rendimientos.json"), MediaType.APPLICATION_JSON));

        List<CryptoYield> fetched =
                new ArgentinaDatosCryptoAdapter(builder.build(), Clock.fixed(FETCHED_AT, ZoneOffset.UTC)).fetch();

        assertThat(fetched).hasSize(64);
        server.verify();
    }
}
