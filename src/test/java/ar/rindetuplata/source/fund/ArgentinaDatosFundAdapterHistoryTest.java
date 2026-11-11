package ar.rindetuplata.source.fund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ar.rindetuplata.source.Fixtures;
import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.SourceRequestException;
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
 * The dated FCI endpoint {@code /fci/mercadoDinero/yyyy/MM/dd}. A day without quotes (weekend or
 * holiday) is a 404 {@code {"error":"Not found"}} from the live API, which is "no data that day", not a failure; the
 * staleness of a backfilled class is measured against the requested day, never against today.
 */
class ArgentinaDatosFundAdapterHistoryTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-11-01T12:00:00Z");
    private static final String BASE = "https://ad.test/v1/finanzas";
    private static final LocalDate DAY = LocalDate.parse("2026-10-01");

    private ArgentinaDatosFundAdapter adapter(RestClient.Builder builder) {
        return new ArgentinaDatosFundAdapter(builder.build(), Clock.fixed(FETCHED_AT, ZoneOffset.UTC));
    }

    @Test
    void fetchDayCallsTheDatedPathAndParsesEveryClassOfThatDay() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/fci/mercadoDinero/2026/10/01")).andRespond(
                withSuccess(Fixtures.bytes("argentinadatos/fci-mercado-dinero-2026-10-01-excerpt.json"),
                        MediaType.APPLICATION_JSON));

        List<FundQuote> quotes = adapter(builder).fetchDay(DAY);

        server.verify();
        assertThat(quotes).hasSize(10);
        assertThat(quotes).allSatisfy(quote -> {
            assertThat(quote.date()).isEqualTo(DAY);
            assertThat(quote.source()).isEqualTo(ArgentinaDatosFundAdapter.SOURCE);
            assertThat(quote.fetchedAt()).isEqualTo(FETCHED_AT);
        });
        FundQuote mercadoFondo = quotes.stream().filter(q -> q.name().equals("Mercado Fondo - Clase A")).findFirst()
                .orElseThrow();
        assertThat(mercadoFondo.vcp()).isNotNull().isPositive();
        assertThat(mercadoFondo.fund()).isEqualTo("Mercado Fondo");
        assertThat(mercadoFondo.shareClass()).isEqualTo("Clase A");
    }

    @Test
    void aBackfilledClassIsStaleOnlyAgainstTheRequestedDayNeverAgainstTheFetchDate() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/fci/mercadoDinero/2026/10/01")).andRespond(
                withSuccess(Fixtures.bytes("argentinadatos/fci-mercado-dinero-2026-10-01-excerpt.json"),
                        MediaType.APPLICATION_JSON));

        List<FundQuote> quotes = adapter(builder).fetchDay(DAY);

        assertThat(quotes).allSatisfy(quote -> assertThat(quote.flags()).extracting(f -> f.code())
                .doesNotContain(FlagCode.STALE));
    }

    @Test
    void aDayWithoutQuotesIs404NotFoundAndAnswersNoQuotes() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/fci/mercadoDinero/2026/10/03")).andRespond(
                withStatus(HttpStatus.NOT_FOUND)
                        .body(Fixtures.bytes("argentinadatos/fci-mercado-dinero-2026-10-03-not-found.json"))
                        .contentType(MediaType.APPLICATION_JSON));

        assertThat(adapter(builder).fetchDay(LocalDate.parse("2026-10-03"))).isEmpty();
        server.verify();
    }

    @Test
    void anyOtherErrorStaysAFailureOfTheSource() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/fci/mercadoDinero/2026/10/01")).andRespond(withServerError());

        assertThatThrownBy(() -> adapter(builder).fetchDay(DAY)).isInstanceOfSatisfying(SourceRequestException.class,
                e -> assertThat(e.status()).isEqualTo(500));
    }
}
