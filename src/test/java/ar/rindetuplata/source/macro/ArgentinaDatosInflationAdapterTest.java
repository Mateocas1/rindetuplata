package ar.rindetuplata.source.macro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Fixtures;
import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.SourceException;
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

class ArgentinaDatosInflationAdapterTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-10-14T12:00:00Z");

    private final List<MacroObservation> observations =
            ArgentinaDatosInflationAdapter.parse(Fixtures.bytes("argentinadatos/inflacion.json"), FETCHED_AT);

    @Test
    void parsesTheLastTwentyFourMonthsNewestFirst() {
        assertThat(observations).hasSize(24);
        assertThat(observations.get(0).date()).isEqualTo(LocalDate.parse("2026-09-30"));
        assertThat(observations.get(0).value()).isEqualByComparingTo("0.019");
        assertThat(observations.get(23).date()).isEqualTo(LocalDate.parse("2024-10-31"));
        assertThat(observations.get(23).value()).isEqualByComparingTo("0.027");
    }

    @Test
    void isMonthlyInflationFromAnAggregator() {
        assertThat(observations).allSatisfy(o -> {
            assertThat(o.variable()).isEqualTo(MacroVariable.MONTHLY_INFLATION);
            assertThat(o.confidence()).isEqualTo(Confidence.AGGREGATOR_AUTOMATIC);
            assertThat(o.source().official()).isFalse();
            assertThat(o.flags()).isEmpty();
        });
    }

    @Test
    void aRowWithoutFechaIsInvalidInsteadOfCrashingTheSort() {
        String json = """
                [{"fecha":null,"valor":1.9},{"fecha":"2026-08-31","valor":2.1},{"fecha":"2026-09-30","valor":1.9}]
                """;

        List<MacroObservation> parsed = ArgentinaDatosInflationAdapter.parse(json.getBytes(StandardCharsets.UTF_8),
                FETCHED_AT);

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
    void onlyMonthlyInflationIsSupported() {
        ArgentinaDatosInflationAdapter adapter = new ArgentinaDatosInflationAdapter(RestClient.create(),
                Clock.fixed(FETCHED_AT, ZoneOffset.UTC));

        assertThat(adapter.supports(MacroVariable.MONTHLY_INFLATION)).isTrue();
        assertThat(adapter.supports(MacroVariable.EXPECTED_INFLATION_12M)).isFalse();
        assertThatThrownBy(() -> adapter.fetch(MacroVariable.PSP_BENCHMARK_TNA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedBodiesAreSourceExceptions() {
        assertThatThrownBy(() -> ArgentinaDatosInflationAdapter.parse("{}".getBytes(StandardCharsets.UTF_8), FETCHED_AT))
                .isInstanceOf(SourceException.class);
    }

    @Test
    void fetchCallsTheInflationPath() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ad.test/v1/finanzas");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ad.test/v1/finanzas/indices/inflacion"))
                .andRespond(withSuccess(Fixtures.bytes("argentinadatos/inflacion.json"), MediaType.APPLICATION_JSON));

        assertThat(new ArgentinaDatosInflationAdapter(builder.build(), Clock.fixed(FETCHED_AT, ZoneOffset.UTC))
                .fetch(MacroVariable.MONTHLY_INFLATION)).hasSize(24);
        server.verify();
    }
}
