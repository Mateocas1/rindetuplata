package ar.rindetuplata.source.macro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ar.rindetuplata.source.Fixtures;
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

/**
 * BCRA monetary statistics take {@code desde} and {@code hasta}, probed live on 2026-11-01 (with the
 * recorded answers): the range is inclusive, the {@code limit} may be at most 3000 (3001 is a 400 "El límite no puede
 * superar los 3000 registros.") and an inverted range is a 400 "Fecha desde no debe ser mayor a la fecha hasta.".
 */
class BcraMonetariasClientRangeTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-11-01T12:00:00Z");
    private static final String BASE = "https://bcra.test/estadisticas/v4.0";

    private BcraMonetariasClient client(RestClient.Builder builder) {
        return new BcraMonetariasClient(builder.build(), Clock.fixed(FETCHED_AT, ZoneOffset.UTC), 5);
    }

    @Test
    void fetchRangeAsksForTheDatesWithTheMaximumLimit() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/monetarias/27?desde=2025-01-01&hasta=2025-06-30&limit=3000"))
                .andRespond(withSuccess(Fixtures.bytes("bcra/monetarias-27-range-2025-01-01-2025-06-30.json"),
                        MediaType.APPLICATION_JSON));

        List<MacroObservation> observations = client(builder).fetchRange(MacroVariable.MONTHLY_INFLATION,
                LocalDate.parse("2025-01-01"), LocalDate.parse("2025-06-30"));

        server.verify();
        assertThat(observations).extracting(MacroObservation::date).containsExactly(
                LocalDate.parse("2025-06-30"), LocalDate.parse("2025-05-31"), LocalDate.parse("2025-04-30"),
                LocalDate.parse("2025-03-31"), LocalDate.parse("2025-02-28"), LocalDate.parse("2025-01-31"));
        assertThat(observations.get(0).value()).isEqualByComparingTo("0.016");
        assertThat(observations.get(5).value()).isEqualByComparingTo("0.022");
        assertThat(observations).allSatisfy(o -> {
            assertThat(o.variable()).isEqualTo(MacroVariable.MONTHLY_INFLATION);
            assertThat(o.source()).isEqualTo(BcraMonetariasClient.SOURCE);
            assertThat(o.fetchedAt()).isEqualTo(FETCHED_AT);
        });
    }

    @Test
    void anInvertedRangeIsTheTypedBcraErrorWithItsMessage() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/monetarias/27?desde=2025-06-30&hasta=2025-01-01&limit=3000"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body(Fixtures.bytes("bcra/monetarias-range-inverted.json"))
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(builder).fetchRange(MacroVariable.MONTHLY_INFLATION,
                LocalDate.parse("2025-06-30"), LocalDate.parse("2025-01-01")))
                .isInstanceOfSatisfying(BcraApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(400);
                    assertThat(e.errorMessages()).containsExactly("Fecha desde no debe ser mayor a la fecha hasta.");
                });
    }
}
