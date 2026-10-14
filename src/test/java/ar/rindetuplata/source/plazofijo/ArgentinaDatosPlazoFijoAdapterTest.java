package ar.rindetuplata.source.plazofijo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Fixtures;
import ar.rindetuplata.source.FlagCode;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ArgentinaDatosPlazoFijoAdapterTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-10-14T12:00:00Z");

    private final List<BankPlazoFijoRate> rows =
            ArgentinaDatosPlazoFijoAdapter.parse(Fixtures.bytes("argentinadatos/plazo-fijo.json"), FETCHED_AT);

    private BankPlazoFijoRate bank(String name) {
        return rows.stream().filter(r -> r.bankName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void parsesEveryInstitution() {
        assertThat(rows).hasSize(32);
    }

    @Test
    void keepsTnaAsGivenAsFractions() {
        BankPlazoFijoRate nacion = bank("BANCO DE LA NACION ARGENTINA");

        assertThat(nacion.clientTna()).isEqualByComparingTo("0.1925");
        assertThat(nacion.terms()).hasSize(3);
        assertThat(nacion.terms().get(0).minDays()).isEqualTo(60);
        assertThat(nacion.terms().get(0).maxDays()).isEqualTo(60);
        assertThat(nacion.terms().get(0).tna()).isEqualByComparingTo("0.1975");
        assertThat(nacion.terms().get(2).tna()).isEqualByComparingTo("0.23");
        assertThat(nacion.source().official()).isFalse();
        assertThat(nacion.confidence()).isEqualTo(Confidence.AGGREGATOR_AUTOMATIC);
        assertThat(nacion.fetchedAt()).isEqualTo(FETCHED_AT);
        assertThat(nacion.dataDate()).isEqualTo(LocalDate.parse("2026-10-14"));
    }

    @Test
    void distinguishesClientFromNonClientRates() {
        BankPlazoFijoRate provincia = bank("BANCO DE LA PROVINCIA DE BUENOS AIRES");

        assertThat(provincia.clientTna()).isEqualByComparingTo("0.21");
        assertThat(provincia.nonClientTna()).isEqualByComparingTo("0.22");
    }

    @Test
    void zeroNonClientRateMeansNotOfferedAndIsNotInvalid() {
        BankPlazoFijoRate nacion = bank("BANCO DE LA NACION ARGENTINA");

        assertThat(nacion.nonClientTna()).isNull();
        assertThat(nacion.isInvalid()).isFalse();
        assertThat(nacion.flags()).isEmpty();
        assertThat(rows).filteredOn(r -> r.nonClientTna() == null).hasSize(8);
    }

    @Test
    void parsesAmountBandsAndTermRangesOfMultiTierBanks() {
        TermRate first = bank("BANCO MACRO S.A.").terms().get(0);

        assertThat(bank("BANCO MACRO S.A.").terms()).hasSize(16);
        assertThat(first.minAmount()).isEqualByComparingTo("1");
        assertThat(first.maxAmount()).isEqualByComparingTo("4999999.99");
        assertThat(first.minDays()).isEqualTo(30);
        assertThat(first.maxDays()).isEqualTo(35);
        assertThat(first.tna()).isEqualByComparingTo("0.195");
    }

    @Test
    void institutionsWithoutTermListsHaveEmptyTerms() {
        assertThat(bank("BANCO PATAGONIA S.A.").terms()).isEmpty();
        assertThat(bank("BANCO PATAGONIA S.A.").clientTna()).isEqualByComparingTo("0.16");
    }

    @Test
    void zeroClientRateAndOutOfRangeRatesAreInvalid() {
        String json = """
                [{"entidad":"A","tnaClientes":0,"tnaNoClientes":0.2,"tasas":[]},
                 {"entidad":"C","tnaClientes":0.2,"tnaNoClientes":4,"tasas":[{"plazoMinDias":30,"plazoMaxDias":30,"tna":-0.1}]}]
                """;

        List<BankPlazoFijoRate> parsed =
                ArgentinaDatosPlazoFijoAdapter.parse(json.getBytes(StandardCharsets.UTF_8), FETCHED_AT);

        assertThat(parsed.get(0).flags()).extracting(ValidationFlag::code, ValidationFlag::severity)
                .contains(org.assertj.core.groups.Tuple.tuple(FlagCode.NON_POSITIVE_RATE, Severity.INVALID));
        assertThat(parsed.get(1).flags()).extracting(ValidationFlag::code)
                .contains(FlagCode.RATE_ABOVE_MAXIMUM, FlagCode.NON_POSITIVE_RATE);
        assertThat(parsed).allMatch(BankPlazoFijoRate::isInvalid);
    }

    @Test
    void missingClientRateIsAbsentWhenTheNonClientRateIsValid() {
        String json = """
                [{"entidad":"B","tnaClientes":null,"tnaNoClientes":0.2}]
                """;

        BankPlazoFijoRate parsed =
                ArgentinaDatosPlazoFijoAdapter.parse(json.getBytes(StandardCharsets.UTF_8), FETCHED_AT).get(0);

        assertThat(parsed.clientTna()).isNull();
        assertThat(parsed.nonClientTna()).isEqualByComparingTo("0.2");
        assertThat(parsed.flags()).isEmpty();
        assertThat(parsed.isInvalid()).isFalse();
    }

    @Test
    void rowWithoutClientOrNonClientRateIsInvalid() {
        String json = """
                [{"entidad":"D","tnaClientes":null,"tnaNoClientes":0},
                 {"entidad":"E"}]
                """;

        List<BankPlazoFijoRate> parsed =
                ArgentinaDatosPlazoFijoAdapter.parse(json.getBytes(StandardCharsets.UTF_8), FETCHED_AT);

        assertThat(parsed).hasSize(2).allSatisfy(rate -> {
            assertThat(rate.isInvalid()).isTrue();
            assertThat(rate.flags()).extracting(ValidationFlag::code).containsExactly(FlagCode.NON_POSITIVE_RATE);
        });
    }

    @Test
    void malformedBodiesAreSourceExceptions() {
        assertThatThrownBy(() -> ArgentinaDatosPlazoFijoAdapter.parse("{}".getBytes(StandardCharsets.UTF_8), FETCHED_AT))
                .isInstanceOf(SourceException.class);
        assertThatThrownBy(() -> ArgentinaDatosPlazoFijoAdapter.parse("oops".getBytes(StandardCharsets.UTF_8), FETCHED_AT))
                .isInstanceOf(SourceException.class);
    }

    @Test
    void fetchCallsThePlazoFijoPath() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ad.test/v1/finanzas");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ad.test/v1/finanzas/tasas/plazoFijo"))
                .andRespond(withSuccess(Fixtures.bytes("argentinadatos/plazo-fijo.json"), MediaType.APPLICATION_JSON));

        List<BankPlazoFijoRate> fetched = new ArgentinaDatosPlazoFijoAdapter(builder.build(),
                Clock.fixed(FETCHED_AT, ZoneOffset.UTC)).fetch();

        assertThat(fetched).hasSize(32);
        server.verify();
    }

    @Test
    void fetchSurfacesClientErrors() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ad.test/v1/finanzas");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ad.test/v1/finanzas/tasas/plazoFijo")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        ArgentinaDatosPlazoFijoAdapter adapter =
                new ArgentinaDatosPlazoFijoAdapter(builder.build(), Clock.fixed(FETCHED_AT, ZoneOffset.UTC));

        assertThatThrownBy(adapter::fetch).isInstanceOfSatisfying(SourceRequestException.class,
                e -> assertThat(e.status()).isEqualTo(404));
    }
}
