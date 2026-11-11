package ar.rindetuplata.source.dollar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Fixtures;
import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.SourceException;
import ar.rindetuplata.source.SourceRequestException;
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

/**
 * The ArgentinaDatos MEP series {@code /cotizaciones/dolares/bolsa}, one row per calendar day, read once
 * per backfill run under its own source id so it never collides with the DolarApi rows the daily job stores.
 */
class ArgentinaDatosDollarAdapterTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-11-01T12:00:00Z");
    private static final String BASE = "https://ad.test/v1/cotizaciones";

    private final List<DatedDollarQuote> series = ArgentinaDatosDollarAdapter.parse(
            Fixtures.bytes("argentinadatos/dolares-bolsa-serie-2026-09-28-2026-10-06-excerpt.json"), FETCHED_AT);

    @Test
    void theSourceHasItsOwnIdDistinctFromDolarApi() {
        assertThat(ArgentinaDatosDollarAdapter.SOURCE.id()).isEqualTo("argentinadatos-dolar-bolsa");
        assertThat(ArgentinaDatosDollarAdapter.SOURCE.id()).isNotEqualTo(DolarApiAdapter.SOURCE.id());
        assertThat(ArgentinaDatosDollarAdapter.SOURCE.official()).isFalse();
    }

    @Test
    void parsesEveryDayOfTheSeriesWithItsOwnDate() {
        assertThat(series).hasSize(9);
        assertThat(series).extracting(DatedDollarQuote::date).containsExactly(
                LocalDate.parse("2026-09-28"), LocalDate.parse("2026-09-29"), LocalDate.parse("2026-09-30"),
                LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-03"),
                LocalDate.parse("2026-10-04"), LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-06"));
    }

    @Test
    void keepsBuyAndSellExactlyAsRecorded() {
        DollarQuote quote = series.stream().filter(d -> d.date().equals(LocalDate.parse("2026-10-01")))
                .findFirst().orElseThrow().quote();

        assertThat(quote.buy()).isEqualByComparingTo("1537.2");
        assertThat(quote.sell()).isEqualByComparingTo("1548.7");
        assertThat(quote.source()).isEqualTo(ArgentinaDatosDollarAdapter.SOURCE);
        assertThat(quote.fetchedAt()).isEqualTo(FETCHED_AT);
        assertThat(quote.updatedAt()).isNull();
        assertThat(quote.confidence()).isEqualTo(Confidence.AGGREGATOR_AUTOMATIC);
        assertThat(quote.flags()).isEmpty();
    }

    @Test
    void aMissingOrNonPositivePriceIsFlaggedInvalid() {
        String json = "[{\"casa\":\"bolsa\",\"compra\":0,\"venta\":null,\"fecha\":\"2026-10-01\"}]";

        DollarQuote quote = ArgentinaDatosDollarAdapter.parse(json.getBytes(StandardCharsets.UTF_8), FETCHED_AT)
                .get(0).quote();

        assertThat(quote.sell()).isNull();
        assertThat(quote.flags()).isNotEmpty().allSatisfy(flag -> {
            assertThat(flag.code()).isEqualTo(FlagCode.INVALID_QUOTE);
            assertThat(flag.severity()).isEqualTo(Severity.INVALID);
        });
    }

    @Test
    void aRowOfAnotherExchangeRateOrWithoutADateIsASourceException() {
        assertThatThrownBy(() -> ArgentinaDatosDollarAdapter.parse(
                "[{\"casa\":\"oficial\",\"compra\":1,\"venta\":2,\"fecha\":\"2026-10-01\"}]"
                        .getBytes(StandardCharsets.UTF_8), FETCHED_AT)).isInstanceOf(SourceException.class)
                .hasMessageContaining("oficial");
        assertThatThrownBy(() -> ArgentinaDatosDollarAdapter.parse(
                "[{\"casa\":\"bolsa\",\"compra\":1,\"venta\":2}]".getBytes(StandardCharsets.UTF_8), FETCHED_AT))
                .isInstanceOf(SourceException.class).hasMessageContaining("fecha");
        assertThatThrownBy(() -> ArgentinaDatosDollarAdapter.parse("{\"a\":1}".getBytes(StandardCharsets.UTF_8),
                FETCHED_AT)).isInstanceOf(SourceException.class);
    }

    @Test
    void fetchSeriesCallsTheBolsaPathOnce() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/dolares/bolsa")).andRespond(withSuccess(
                Fixtures.bytes("argentinadatos/dolares-bolsa-serie-2026-09-28-2026-10-06-excerpt.json"),
                MediaType.APPLICATION_JSON));
        ArgentinaDatosDollarAdapter adapter = new ArgentinaDatosDollarAdapter(builder.build(),
                Clock.fixed(FETCHED_AT, ZoneOffset.UTC));

        assertThat(adapter.fetchSeries()).hasSize(9);
        server.verify();
    }

    @Test
    void aServerErrorIsAFailureOfTheSource() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/dolares/bolsa")).andRespond(withServerError());
        ArgentinaDatosDollarAdapter adapter = new ArgentinaDatosDollarAdapter(builder.build(),
                Clock.fixed(FETCHED_AT, ZoneOffset.UTC));

        assertThatThrownBy(adapter::fetchSeries).isInstanceOfSatisfying(SourceRequestException.class,
                e -> assertThat(e.status()).isEqualTo(500));
    }
}
