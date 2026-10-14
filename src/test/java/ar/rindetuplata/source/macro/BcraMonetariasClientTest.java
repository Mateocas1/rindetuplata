package ar.rindetuplata.source.macro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Fixtures;
import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.SourceException;
import ar.rindetuplata.source.SourceRequestException;
import java.net.SocketTimeoutException;
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

class BcraMonetariasClientTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-10-14T12:00:00Z");
    private static final String BASE = "https://bcra.test/estadisticas/v4.0";

    @Test
    void parsesMonthlyInflationAsFractionsNewestFirst() {
        List<MacroObservation> observations = BcraMonetariasClient.parse(Fixtures.bytes("bcra/monetarias-27.json"),
                MacroVariable.MONTHLY_INFLATION, FETCHED_AT);

        assertThat(observations).hasSize(5);
        assertThat(observations.get(0).date()).isEqualTo(LocalDate.parse("2026-09-30"));
        assertThat(observations.get(0).value()).isEqualByComparingTo("0.019");
        assertThat(observations).extracting(MacroObservation::date).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(observations).allSatisfy(o -> {
            assertThat(o.variable()).isEqualTo(MacroVariable.MONTHLY_INFLATION);
            assertThat(o.confidence()).isEqualTo(Confidence.OFFICIAL_AUTOMATIC);
            assertThat(o.source().official()).isTrue();
            assertThat(o.fetchedAt()).isEqualTo(FETCHED_AT);
            assertThat(o.flags()).isEmpty();
        });
    }

    @Test
    void parsesTheRemExpectedInflation() {
        List<MacroObservation> observations = BcraMonetariasClient.parse(Fixtures.bytes("bcra/monetarias-29.json"),
                MacroVariable.EXPECTED_INFLATION_12M, FETCHED_AT);

        assertThat(observations.get(0).value()).isEqualByComparingTo("0.211");
        assertThat(observations.get(4).date()).isEqualTo(LocalDate.parse("2026-05-31"));
        assertThat(observations.get(4).value()).isEqualByComparingTo("0.233");
    }

    @Test
    void parsesThePspBenchmark() {
        List<MacroObservation> observations = BcraMonetariasClient.parse(Fixtures.bytes("bcra/monetarias-1196.json"),
                MacroVariable.PSP_BENCHMARK_TNA, FETCHED_AT);

        assertThat(observations.get(0).date()).isEqualTo(LocalDate.parse("2026-10-09"));
        assertThat(observations.get(0).value()).isEqualByComparingTo("0.1413");
    }

    @Test
    void variableIdsAreTheBcraOnes() {
        assertThat(MacroVariable.MONTHLY_INFLATION.bcraId()).isEqualTo(27);
        assertThat(MacroVariable.EXPECTED_INFLATION_12M.bcraId()).isEqualTo(29);
        assertThat(MacroVariable.PSP_BENCHMARK_TNA.bcraId()).isEqualTo(1196);
    }

    @Test
    void negativeInflationIsAValidObservation() {
        String json = """
                {"status":200,"results":[{"idVariable":27,"detalle":[{"fecha":"2026-01-31","valor":-0.3}]}]}
                """;

        List<MacroObservation> parsed = BcraMonetariasClient.parse(json.getBytes(StandardCharsets.UTF_8),
                MacroVariable.MONTHLY_INFLATION, FETCHED_AT);

        assertThat(parsed.get(0).value()).isEqualByComparingTo("-0.003");
        assertThat(parsed.get(0).flags()).isEmpty();
    }

    @Test
    void missingValueIsInvalid() {
        String json = """
                {"status":200,"results":[{"idVariable":29,"detalle":[{"fecha":"2026-01-31","valor":null}]}]}
                """;

        MacroObservation parsed = BcraMonetariasClient.parse(json.getBytes(StandardCharsets.UTF_8),
                MacroVariable.EXPECTED_INFLATION_12M, FETCHED_AT).get(0);

        assertThat(parsed.value()).isNull();
        assertThat(parsed.flags()).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo(FlagCode.MISSING_VALUE);
            assertThat(f.severity()).isEqualTo(Severity.INVALID);
        });
        assertThat(parsed.isInvalid()).isTrue();
    }

    @Test
    void aRowWithoutFechaIsInvalidInsteadOfCrashingTheSort() {
        String json = """
                {"status":200,"results":[{"idVariable":27,"detalle":[
                  {"valor":1.9},
                  {"fecha":"2026-08-31","valor":2.1},
                  {"fecha":"2026-09-30","valor":1.9}]}]}
                """;

        List<MacroObservation> parsed = BcraMonetariasClient.parse(json.getBytes(StandardCharsets.UTF_8),
                MacroVariable.MONTHLY_INFLATION, FETCHED_AT);

        assertThat(parsed).hasSize(3);
        assertThat(parsed.subList(0, 2)).extracting(MacroObservation::date)
                .containsExactly(LocalDate.parse("2026-09-30"), LocalDate.parse("2026-08-31"));
        MacroObservation undated = parsed.get(2);
        assertThat(undated.date()).isNull();
        assertThat(undated.isInvalid()).isTrue();
        assertThat(undated.flags()).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo(FlagCode.MISSING_VALUE);
            assertThat(f.severity()).isEqualTo(Severity.INVALID);
            assertThat(f.message()).contains("date");
        });
    }

    @Test
    void emptyResultsGiveNoObservations() {
        assertThat(BcraMonetariasClient.parse("{\"status\":200,\"results\":[]}".getBytes(StandardCharsets.UTF_8),
                MacroVariable.MONTHLY_INFLATION, FETCHED_AT)).isEmpty();
    }

    @Test
    void anotherVariableInTheBodyIsASourceException() {
        assertThatThrownBy(() -> BcraMonetariasClient.parse(Fixtures.bytes("bcra/monetarias-29.json"),
                MacroVariable.MONTHLY_INFLATION, FETCHED_AT)).isInstanceOf(SourceException.class)
                .hasMessageContaining("29");
    }

    @Test
    void malformedBodiesAreSourceExceptions() {
        assertThatThrownBy(() -> BcraMonetariasClient.parse("<html/>".getBytes(StandardCharsets.UTF_8),
                MacroVariable.MONTHLY_INFLATION, FETCHED_AT)).isInstanceOf(SourceException.class);
        assertThatThrownBy(() -> BcraMonetariasClient.parse("{\"status\":200}".getBytes(StandardCharsets.UTF_8),
                MacroVariable.MONTHLY_INFLATION, FETCHED_AT)).isInstanceOf(SourceException.class);
    }

    private BcraMonetariasClient client(RestClient.Builder builder) {
        return new BcraMonetariasClient(builder.build(), Clock.fixed(FETCHED_AT, ZoneOffset.UTC), 5);
    }

    @Test
    void fetchCallsTheVariablePathWithTheLimit() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/monetarias/29?limit=5"))
                .andRespond(withSuccess(Fixtures.bytes("bcra/monetarias-29.json"), MediaType.APPLICATION_JSON));

        List<MacroObservation> fetched = client(builder).fetch(MacroVariable.EXPECTED_INFLATION_12M);

        assertThat(fetched).hasSize(5);
        server.verify();
    }

    @Test
    void badRequestBecomesATypedBcraErrorWithItsMessages() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/monetarias/27?limit=5"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body(Fixtures.bytes("bcra/monetarias-unknown-variable.json")).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(builder).fetch(MacroVariable.MONTHLY_INFLATION))
                .isInstanceOfSatisfying(BcraApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(400);
                    assertThat(e.errorMessages()).containsExactly(
                            "Parámetro erróneo: Validar formato del id de la variable ingresada.");
                    assertThat(e.getMessage()).contains("Parámetro erróneo");
                });
    }

    @Test
    void serverErrorWithoutBcraBodyStaysAGenericRequestException() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/monetarias/27?limit=5")).andRespond(withServerError());

        assertThatThrownBy(() -> client(builder).fetch(MacroVariable.MONTHLY_INFLATION))
                .isInstanceOfSatisfying(SourceRequestException.class, e -> {
                    assertThat(e).isNotInstanceOf(BcraApiException.class);
                    assertThat(e.status()).isEqualTo(500);
                });
    }

    @Test
    void timeoutAndMalformedBodiesAreSourceExceptions() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/monetarias/27?limit=5")).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });
        server.expect(requestTo(BASE + "/monetarias/27?limit=5"))
                .andRespond(withSuccess("garbage", MediaType.APPLICATION_JSON));
        BcraMonetariasClient client = client(builder);

        assertThatThrownBy(() -> client.fetch(MacroVariable.MONTHLY_INFLATION)).isInstanceOf(SourceException.class)
                .hasRootCauseInstanceOf(SocketTimeoutException.class);
        assertThatThrownBy(() -> client.fetch(MacroVariable.MONTHLY_INFLATION)).isInstanceOf(SourceException.class);
    }
}
