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
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DolarApiAdapterTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-10-14T12:00:00Z");

    @Test
    void parsesTheMepQuote() {
        DollarQuote quote = DolarApiAdapter.parse(Fixtures.bytes("dolarapi/dolar-bolsa.json"), FETCHED_AT);

        assertThat(quote.buy()).isEqualByComparingTo("1534.2");
        assertThat(quote.sell()).isEqualByComparingTo("1539.4");
        assertThat(quote.updatedAt()).isEqualTo(Instant.parse("2026-10-13T20:57:00Z"));
        assertThat(quote.fetchedAt()).isEqualTo(FETCHED_AT);
        assertThat(quote.confidence()).isEqualTo(Confidence.AGGREGATOR_AUTOMATIC);
        assertThat(quote.source().official()).isFalse();
        assertThat(quote.flags()).isEmpty();
    }

    @Test
    void nonPositiveOrMissingPricesAreInvalid() {
        String json = """
                {"moneda":"USD","casa":"bolsa","nombre":"Bolsa","compra":0,"venta":null,"fechaActualizacion":"2026-10-13T20:57:00.000Z"}
                """;

        DollarQuote quote = DolarApiAdapter.parse(json.getBytes(StandardCharsets.UTF_8), FETCHED_AT);

        assertThat(quote.sell()).isNull();
        assertThat(quote.flags()).isNotEmpty().allSatisfy(f -> {
            assertThat(f.code()).isEqualTo(FlagCode.INVALID_QUOTE);
            assertThat(f.severity()).isEqualTo(Severity.INVALID);
        });
        assertThat(quote.isInvalid()).isTrue();
    }

    @Test
    void anotherExchangeRateTypeIsASourceException() {
        String json = """
                {"moneda":"USD","casa":"oficial","nombre":"Oficial","compra":1,"venta":2,"fechaActualizacion":"2026-10-13T20:57:00.000Z"}
                """;

        assertThatThrownBy(() -> DolarApiAdapter.parse(json.getBytes(StandardCharsets.UTF_8), FETCHED_AT))
                .isInstanceOf(SourceException.class).hasMessageContaining("bolsa");
    }

    @Test
    void malformedBodiesAreSourceExceptions() {
        assertThatThrownBy(() -> DolarApiAdapter.parse("[]".getBytes(StandardCharsets.UTF_8), FETCHED_AT))
                .isInstanceOf(SourceException.class);
        assertThatThrownBy(() -> DolarApiAdapter.parse("???".getBytes(StandardCharsets.UTF_8), FETCHED_AT))
                .isInstanceOf(SourceException.class);
        assertThatThrownBy(() -> DolarApiAdapter.parse(("{\"casa\":\"bolsa\",\"compra\":1,\"venta\":2,"
                + "\"fechaActualizacion\":\"soon\"}").getBytes(StandardCharsets.UTF_8), FETCHED_AT))
                .isInstanceOf(SourceException.class);
    }

    @Test
    void fetchCallsTheBolsaPath() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://dolarapi.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://dolarapi.test/v1/dolares/bolsa"))
                .andRespond(withSuccess(Fixtures.bytes("dolarapi/dolar-bolsa.json"), MediaType.APPLICATION_JSON));

        DollarQuote quote = new DolarApiAdapter(builder.build(), Clock.fixed(FETCHED_AT, ZoneOffset.UTC)).fetchMep();

        assertThat(quote.sell()).isEqualByComparingTo("1539.4");
        server.verify();
    }

    @Test
    void fetchSurfacesServerErrors() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://dolarapi.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://dolarapi.test/v1/dolares/bolsa")).andRespond(withServerError());
        DolarApiAdapter adapter = new DolarApiAdapter(builder.build(), Clock.fixed(FETCHED_AT, ZoneOffset.UTC));

        assertThatThrownBy(adapter::fetchMep).isInstanceOfSatisfying(SourceRequestException.class,
                e -> assertThat(e.status()).isEqualTo(500));
    }
}
