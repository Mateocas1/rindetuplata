package ar.rindetuplata.source.plazofijo;

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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BcraPlazoFijoAdapterTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-10-14T12:00:00Z");

    private final List<OfficialPlazoFijoRate> rows =
            BcraPlazoFijoAdapter.parse(Fixtures.bytes("bcra/transparencia-plazos-fijos.json"), FETCHED_AT);

    private OfficialPlazoFijoRate row(Predicate<OfficialPlazoFijoRate> predicate) {
        return rows.stream().filter(predicate).findFirst().orElseThrow();
    }

    private static List<FlagCode> codes(OfficialPlazoFijoRate rate) {
        return rate.flags().stream().map(ValidationFlag::code).toList();
    }

    @Test
    void parsesEveryRecordedRowWithoutDroppingAny() {
        assertThat(rows).hasSize(33);
    }

    @Test
    void normalizesTeaFromPercentToFractionAndKeepsTheRowFields() {
        OfficialPlazoFijoRate brubank = row(r -> r.entityCode() == 143 && r.productName().equals("PLAZO FIJO PESOS")
                && r.termCode() == 1);

        assertThat(brubank.tea()).isEqualByComparingTo("0.2075");
        assertThat(brubank.entityName()).isEqualTo("BRUBANK S.A.U.");
        assertThat(brubank.minimumAmount()).isEqualByComparingTo("500");
        assertThat(brubank.channel()).isEqualTo("Electrónico");
        assertThat(brubank.dataDate()).isEqualTo(LocalDate.parse("2026-10-13"));
        assertThat(brubank.fetchedAt()).isEqualTo(FETCHED_AT);
        assertThat(brubank.segment()).isEqualTo(Segment.SAVER);
        assertThat(brubank.confidence()).isEqualTo(Confidence.OFFICIAL_AUTOMATIC);
        assertThat(brubank.source().official()).isTrue();
        assertThat(brubank.tea()).isInstanceOf(BigDecimal.class);
    }

    @Test
    void handCheckedValuesMatchTheJson() {
        assertThat(row(r -> r.entityCode() == 7 && r.productName().equals("PFTRADICIONAL") && r.termCode() == 4)
                .tea()).isEqualByComparingTo("0.1937");
        assertThat(row(r -> r.entityCode() == 45030 && r.termCode() == 3).tea()).isEqualByComparingTo("0.2436");
        assertThat(row(r -> r.entityCode() == 384 && r.productName().equals("PF TRADICIONAL") && r.termCode() == 5)
                .tea()).isEqualByComparingTo("0.28");
    }

    @Test
    void termCodeIsKeptRawAndFlaggedUndocumentedOnEveryRow() {
        assertThat(rows).allSatisfy(r -> assertThat(r.flags()).anySatisfy(f -> {
            assertThat(f.code()).isEqualTo(FlagCode.TERM_CODE_UNDOCUMENTED);
            assertThat(f.severity()).isEqualTo(Severity.WARNING);
        }));
        assertThat(rows).filteredOn(r -> r.entityCode() == 384 && r.productName().equals("PF TRADICIONAL"))
                .extracting(OfficialPlazoFijoRate::termCode).containsExactly(1, 2, 3, 4, 5);
        assertThat(rows).filteredOn(r -> r.termCode() != null).extracting(OfficialPlazoFijoRate::termCode)
                .allMatch(code -> code >= 1 && code <= 5);
    }

    @Test
    void nullTermCodeIsKeptAsNull() {
        assertThat(row(r -> r.entityCode() == 15).termCode()).isNull();
    }

    @Test
    void sameProductCanHaveDifferentTeaPerTermCode() {
        assertThat(rows).filteredOn(r -> r.entityCode() == 384 && r.productName().equals("PF TRADICIONAL"))
                .extracting(OfficialPlazoFijoRate::tea)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("0.2436"), new BigDecimal("0.2412"), new BigDecimal("0.2507"),
                        new BigDecimal("0.2659"), new BigDecimal("0.28"));
    }

    @Test
    void zeroTeaRowsAreInvalidButKept() {
        List<OfficialPlazoFijoRate> zero = rows.stream().filter(r -> r.tea().signum() == 0).toList();

        assertThat(zero).hasSize(4);
        assertThat(zero).allSatisfy(r -> assertThat(r.flags()).anySatisfy(f -> {
            assertThat(f.code()).isEqualTo(FlagCode.NON_POSITIVE_RATE);
            assertThat(f.severity()).isEqualTo(Severity.INVALID);
        }));
        assertThat(zero).allMatch(OfficialPlazoFijoRate::isInvalid);
    }

    @Test
    void validRowsAreNotInvalid() {
        assertThat(row(r -> r.entityCode() == 143 && r.productName().equals("PLAZO FIJO PESOS") && r.termCode() == 2)
                .isInvalid()).isFalse();
    }

    @Test
    void legalEntityDollarAndUvaProductsAreClassifiedOutOfTheSaverSegment() {
        assertThat(row(r -> r.productName().equals("PF PPJJ")).segment()).isEqualTo(Segment.LEGAL_ENTITY);
        assertThat(row(r -> r.productName().equals("PF PRECANCELABLE PPJJ")).segment()).isEqualTo(Segment.LEGAL_ENTITY);
        assertThat(row(r -> r.productName().equals("PLAZO FIJO EN DOLARES")).segment()).isEqualTo(Segment.FOREIGN_CURRENCY);
        assertThat(row(r -> r.productName().equals("PF TRADIC. DOLAR SUCURSAL")).segment())
                .isEqualTo(Segment.FOREIGN_CURRENCY);
        assertThat(row(r -> r.productName().equals("PFUVA")).segment()).isEqualTo(Segment.UVA_ADJUSTED);
        assertThat(rows).filteredOn(r -> r.segment() == Segment.SAVER).extracting(OfficialPlazoFijoRate::productName)
                .doesNotContain("PF PPJJ", "PFUVA", "PLAZO FIJO EN DOLARES");
    }

    @Test
    void rowsOlderThanFortyFiveDaysAreStaleWarnings() {
        List<OfficialPlazoFijoRate> later = BcraPlazoFijoAdapter
                .parse(Fixtures.bytes("bcra/transparencia-plazos-fijos.json"), Instant.parse("2026-11-20T12:00:00Z"));

        assertThat(rows).noneMatch(r -> codes(r).contains(FlagCode.STALE));
        assertThat(later).filteredOn(r -> r.dataDate().equals(LocalDate.parse("2026-09-18")))
                .isNotEmpty()
                .allSatisfy(r -> assertThat(r.flags()).anySatisfy(f -> {
                    assertThat(f.code()).isEqualTo(FlagCode.STALE);
                    assertThat(f.severity()).isEqualTo(Severity.WARNING);
                }));
        assertThat(later).filteredOn(r -> r.dataDate().equals(LocalDate.parse("2026-10-13")))
                .noneMatch(r -> codes(r).contains(FlagCode.STALE));
    }

    @Test
    void nullAndAbsurdRatesAreInvalid() {
        String json = """
                {"status":200,"results":[
                 {"montoMinimoInvertir":1,"plazoMinimoInvertirDias":1,"canalConstitucion":"X","tasaEfectivaAnualMinima":null,
                  "codigoEntidad":1,"descripcionEntidad":"A","fechaInformacion":"2026-10-13","nombreCorto":"PF"},
                 {"montoMinimoInvertir":1,"plazoMinimoInvertirDias":1,"canalConstitucion":"X","tasaEfectivaAnualMinima":301,
                  "codigoEntidad":1,"descripcionEntidad":"A","fechaInformacion":"2026-10-13","nombreCorto":"PF"}]}
                """;

        List<OfficialPlazoFijoRate> parsed = BcraPlazoFijoAdapter.parse(json.getBytes(StandardCharsets.UTF_8), FETCHED_AT);

        assertThat(parsed.get(0).tea()).isNull();
        assertThat(codes(parsed.get(0))).contains(FlagCode.NON_POSITIVE_RATE);
        assertThat(codes(parsed.get(1))).contains(FlagCode.RATE_ABOVE_MAXIMUM);
        assertThat(parsed).allMatch(OfficialPlazoFijoRate::isInvalid);
    }

    @Test
    void malformedBodiesAreSourceExceptions() {
        assertThatThrownBy(() -> BcraPlazoFijoAdapter.parse("<html>".getBytes(StandardCharsets.UTF_8), FETCHED_AT))
                .isInstanceOf(SourceException.class);
        assertThatThrownBy(() -> BcraPlazoFijoAdapter.parse("{\"status\":200}".getBytes(StandardCharsets.UTF_8), FETCHED_AT))
                .isInstanceOf(SourceException.class).hasMessageContaining("results");
        assertThatThrownBy(() -> BcraPlazoFijoAdapter.parse(new byte[0], FETCHED_AT)).isInstanceOf(SourceException.class);
    }

    @Test
    void fetchCallsThePlazosFijosPathAndParsesTheBody() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://bcra.test/transparencia/v1.0");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://bcra.test/transparencia/v1.0/PlazosFijos"))
                .andRespond(withSuccess(Fixtures.bytes("bcra/transparencia-plazos-fijos.json"), MediaType.APPLICATION_JSON));
        BcraPlazoFijoAdapter adapter = new BcraPlazoFijoAdapter(builder.build(), Clock.fixed(FETCHED_AT, ZoneOffset.UTC));

        List<OfficialPlazoFijoRate> fetched = adapter.fetch();

        assertThat(fetched).hasSize(33);
        assertThat(fetched.get(0).fetchedAt()).isEqualTo(FETCHED_AT);
        server.verify();
    }

    @Test
    void fetchSurfacesServerErrors() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://bcra.test/transparencia/v1.0");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://bcra.test/transparencia/v1.0/PlazosFijos")).andRespond(withServerError());
        BcraPlazoFijoAdapter adapter = new BcraPlazoFijoAdapter(builder.build(), Clock.fixed(FETCHED_AT, ZoneOffset.UTC));

        assertThatThrownBy(adapter::fetch).isInstanceOfSatisfying(SourceRequestException.class,
                e -> assertThat(e.status()).isEqualTo(500));
    }

    @Test
    void fetchSurfacesMalformedBodies() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://bcra.test/transparencia/v1.0");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://bcra.test/transparencia/v1.0/PlazosFijos"))
                .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
        BcraPlazoFijoAdapter adapter = new BcraPlazoFijoAdapter(builder.build(), Clock.fixed(FETCHED_AT, ZoneOffset.UTC));

        assertThatThrownBy(adapter::fetch).isInstanceOf(SourceException.class);
    }
}
