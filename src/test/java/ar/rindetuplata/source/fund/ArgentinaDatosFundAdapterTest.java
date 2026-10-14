package ar.rindetuplata.source.fund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Fixtures;
import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.Segment;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.SourceException;
import ar.rindetuplata.source.SourceRequestException;
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

class ArgentinaDatosFundAdapterTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-10-14T12:00:00Z");

    private final List<FundQuote> latest =
            ArgentinaDatosFundAdapter.parse(Fixtures.bytes("argentinadatos/fci-mercado-dinero-ultimo.json"), FETCHED_AT);
    private final List<FundQuote> previous =
            ArgentinaDatosFundAdapter.parse(Fixtures.bytes("argentinadatos/fci-mercado-dinero-penultimo.json"), FETCHED_AT);

    private static FundQuote quote(List<FundQuote> quotes, String name) {
        return quotes.stream().filter(q -> q.name().equals(name)).findFirst().orElseThrow();
    }

    private static List<FlagCode> codes(FundQuote quote) {
        return quote.flags().stream().map(ValidationFlag::code).toList();
    }

    @Test
    void parsesEveryRecordedClass() {
        assertThat(latest).hasSize(32);
        assertThat(previous).hasSize(32);
    }

    @Test
    void keepsQuoteFieldsExactlyAsRecorded() {
        FundQuote mercadoFondo = quote(latest, "Mercado Fondo - Clase A");

        assertThat(mercadoFondo.fund()).isEqualTo("Mercado Fondo");
        assertThat(mercadoFondo.shareClass()).isEqualTo("Clase A");
        assertThat(mercadoFondo.horizon()).isEqualTo("corto");
        assertThat(mercadoFondo.date()).isEqualTo(LocalDate.parse("2026-10-09"));
        assertThat(mercadoFondo.vcp()).isEqualByComparingTo("25937.047");
        assertThat(mercadoFondo.ccp()).isEqualByComparingTo("364123369486.064");
        assertThat(mercadoFondo.confidence()).isEqualTo(Confidence.AGGREGATOR_AUTOMATIC);
        assertThat(mercadoFondo.source().official()).isFalse();
        assertThat(mercadoFondo.fetchedAt()).isEqualTo(FETCHED_AT);
        assertThat(mercadoFondo.segment()).isEqualTo(Segment.SAVER);
        assertThat(mercadoFondo.flags()).isEmpty();
    }

    @Test
    void penultimoHoldsTheEarlierQuoteForTheSameClass() {
        assertThat(quote(previous, "Mercado Fondo - Clase A").date()).isEqualTo(LocalDate.parse("2026-10-08"));
        assertThat(quote(previous, "Mercado Fondo - Clase A").vcp()).isEqualByComparingTo("25923.768");
    }

    @Test
    void splitsFundAndClassOnTheFirstSeparatorOnly() {
        FundQuote ley = quote(latest, "Balanz Capital Money Market - Clase L Ley N° 27.743");

        assertThat(ley.fund()).isEqualTo("Balanz Capital Money Market");
        assertThat(ley.shareClass()).isEqualTo("Clase L Ley N° 27.743");
        assertThat(quote(latest, "1810 Ahorro").fund()).isEqualTo("1810 Ahorro");
        assertThat(quote(latest, "1810 Ahorro").shareClass()).isNull();
    }

    @Test
    void deadClassesLaggingTheNewestDateAreStaleWarnings() {
        List<String> stale = latest.stream().filter(q -> codes(q).contains(FlagCode.STALE)).map(FundQuote::name).toList();

        assertThat(stale).containsExactlyInAnyOrder("Cocos Ahorro - Clase D", "Fima Premium Dólares - Clase L",
                "Mercado Fondo - Clase B", "Alianza de Capitales RP", "Axis Ahorro Plus - Clase A");
        assertThat(quote(latest, "Alianza de Capitales RP").flags()).anySatisfy(f -> {
            assertThat(f.code()).isEqualTo(FlagCode.STALE);
            assertThat(f.severity()).isEqualTo(Severity.WARNING);
        });
    }

    @Test
    void stalenessIsRelativeToTheNewestDateOfTheSameResponse() {
        assertThat(previous.stream().filter(q -> codes(q).contains(FlagCode.STALE)).map(FundQuote::name))
                .contains("Mercado Fondo - Clase B", "Cocos Ahorro - Clase D")
                .doesNotContain("Mercado Fondo - Clase A");
    }

    @Test
    void sevenDaysBehindIsNotStaleEightIs() {
        String json = """
                [{"fondo":"A - Clase A","horizonte":"corto","fecha":"2026-10-09","vcp":1,"ccp":1,"patrimonio":1},
                 {"fondo":"B - Clase A","horizonte":"corto","fecha":"2026-10-02","vcp":1,"ccp":1,"patrimonio":1},
                 {"fondo":"C - Clase A","horizonte":"corto","fecha":"2026-10-01","vcp":1,"ccp":1,"patrimonio":1}]
                """;

        Instant fetchedSameDay = Instant.parse("2026-10-09T15:00:00Z");
        List<FundQuote> quotes = ArgentinaDatosFundAdapter.parse(json.getBytes(StandardCharsets.UTF_8), fetchedSameDay);

        assertThat(quotes).extracting(q -> codes(q).contains(FlagCode.STALE)).containsExactly(false, false, true);
    }

    @Test
    void wholeResponseOlderThanSevenDaysBeforeTheFetchIsStale() {
        String json = """
                [{"fondo":"A - Clase A","horizonte":"corto","fecha":"2026-10-07","vcp":1,"ccp":1,"patrimonio":1},
                 {"fondo":"B - Clase A","horizonte":"corto","fecha":"2026-10-06","vcp":1,"ccp":1,"patrimonio":1}]
                """;

        List<FundQuote> quotes = ArgentinaDatosFundAdapter.parse(json.getBytes(StandardCharsets.UTF_8), FETCHED_AT);

        assertThat(quotes).extracting(q -> codes(q).contains(FlagCode.STALE)).containsExactly(false, true);
        assertThat(quote(quotes, "B - Clase A").flags()).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo(FlagCode.STALE);
            assertThat(f.severity()).isEqualTo(Severity.WARNING);
        });
    }

    @Test
    void dollarClassesAreClassifiedOutOfTheSaverSegment() {
        assertThat(quote(latest, "Fima Premium Dólares - Clase A").segment()).isEqualTo(Segment.FOREIGN_CURRENCY);
        assertThat(quote(latest, "Fima Premium - Clase A").segment()).isEqualTo(Segment.SAVER);
    }

    @Test
    void nullAndNonPositiveQuotesAreInvalidButKept() {
        String json = """
                [{"fondo":"A - Clase A","horizonte":"corto","fecha":"2026-10-09","vcp":0,"ccp":null,"patrimonio":null},
                 {"fondo":"B - Clase A","horizonte":"corto","fecha":"2026-10-09","vcp":null,"ccp":1,"patrimonio":1}]
                """;

        List<FundQuote> quotes = ArgentinaDatosFundAdapter.parse(json.getBytes(StandardCharsets.UTF_8), FETCHED_AT);

        assertThat(quotes).hasSize(2).allSatisfy(q -> assertThat(q.flags()).anySatisfy(f -> {
            assertThat(f.code()).isEqualTo(FlagCode.INVALID_QUOTE);
            assertThat(f.severity()).isEqualTo(Severity.INVALID);
        }));
        assertThat(quotes.get(0).ccp()).isNull();
        assertThat(quotes).allMatch(FundQuote::isInvalid);
    }

    @Test
    void malformedBodiesAreSourceExceptions() {
        assertThatThrownBy(() -> ArgentinaDatosFundAdapter.parse("{\"a\":1}".getBytes(StandardCharsets.UTF_8), FETCHED_AT))
                .isInstanceOf(SourceException.class);
        assertThatThrownBy(() -> ArgentinaDatosFundAdapter.parse("[{\"fondo\":\"X\",\"fecha\":\"yesterday\"}]"
                .getBytes(StandardCharsets.UTF_8), FETCHED_AT)).isInstanceOf(SourceException.class);
    }

    private ArgentinaDatosFundAdapter adapter(RestClient.Builder builder) {
        return new ArgentinaDatosFundAdapter(builder.build(), Clock.fixed(FETCHED_AT, ZoneOffset.UTC));
    }

    @Test
    void fetchLatestAndPreviousCallTheirPaths() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ad.test/v1/finanzas");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ad.test/v1/finanzas/fci/mercadoDinero/ultimo"))
                .andRespond(withSuccess(Fixtures.bytes("argentinadatos/fci-mercado-dinero-ultimo.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://ad.test/v1/finanzas/fci/mercadoDinero/penultimo"))
                .andRespond(withSuccess(Fixtures.bytes("argentinadatos/fci-mercado-dinero-penultimo.json"), MediaType.APPLICATION_JSON));
        ArgentinaDatosFundAdapter adapter = adapter(builder);

        assertThat(adapter.fetchLatest()).hasSize(32);
        assertThat(adapter.fetchPrevious()).hasSize(32);
        server.verify();
    }

    @Test
    void fetchSurfacesServerErrors() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ad.test/v1/finanzas");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ad.test/v1/finanzas/fci/mercadoDinero/ultimo")).andRespond(withServerError());
        ArgentinaDatosFundAdapter adapter = adapter(builder);

        assertThatThrownBy(adapter::fetchLatest).isInstanceOfSatisfying(SourceRequestException.class,
                e -> assertThat(e.status()).isEqualTo(500));
    }
}
