package ar.rindetuplata.source.plazofijo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.rindetuplata.source.SourceException;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.SourceStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositePlazoFijoSourceTest {

    private static final SourceRef OFFICIAL = BcraPlazoFijoAdapter.SOURCE;
    private static final SourceRef BANKS = ArgentinaDatosPlazoFijoAdapter.SOURCE;

    private final OfficialPlazoFijoSource officialOk = () -> List.of(
            new OfficialPlazoFijoRate(OFFICIAL, 7, "GALICIA", "PFTRADICIONAL", ar.rindetuplata.source.Segment.SAVER, 4,
                    new java.math.BigDecimal("100"), "Home banking", new java.math.BigDecimal("0.1937"),
                    java.time.LocalDate.parse("2026-10-13"), java.time.Instant.parse("2026-10-14T12:00:00Z"),
                    ar.rindetuplata.source.Confidence.OFFICIAL_AUTOMATIC, List.of()));
    private final BankPlazoFijoSource banksOk = () -> List.of(
            new BankPlazoFijoRate(BANKS, "GALICIA", new java.math.BigDecimal("0.18"), null, List.of(),
                    java.time.LocalDate.parse("2026-10-14"), java.time.Instant.parse("2026-10-14T12:00:00Z"),
                    ar.rindetuplata.source.Confidence.AGGREGATOR_AUTOMATIC, List.of()));
    private final OfficialPlazoFijoSource officialDown = () -> {
        throw new SourceException(OFFICIAL, "down", null);
    };
    private final BankPlazoFijoSource banksDown = () -> {
        throw new SourceException(BANKS, "down", null);
    };

    @Test
    void bothSourcesUpGiveBothResultSetsAndOkStatus() {
        PlazoFijoReport report = new CompositePlazoFijoSource(officialOk, banksOk).fetch();

        assertThat(report.status()).isEqualTo(SourceStatus.OK);
        assertThat(report.official()).hasSize(1);
        assertThat(report.banks()).hasSize(1);
        assertThat(report.failures()).isEmpty();
    }

    @Test
    void officialDownKeepsTheAggregatorAndIsDegraded() {
        PlazoFijoReport report = new CompositePlazoFijoSource(officialDown, banksOk).fetch();

        assertThat(report.status()).isEqualTo(SourceStatus.DEGRADED);
        assertThat(report.official()).isEmpty();
        assertThat(report.banks()).hasSize(1);
        assertThat(report.failures()).singleElement().satisfies(f -> {
            assertThat(f.source()).isEqualTo(OFFICIAL);
            assertThat(f.reason()).contains("down");
        });
    }

    @Test
    void aggregatorDownKeepsTheOfficialSourceAndIsDegraded() {
        PlazoFijoReport report = new CompositePlazoFijoSource(officialOk, banksDown).fetch();

        assertThat(report.status()).isEqualTo(SourceStatus.DEGRADED);
        assertThat(report.official()).hasSize(1);
        assertThat(report.banks()).isEmpty();
        assertThat(report.failures()).extracting(f -> f.source().id()).containsExactly(BANKS.id());
    }

    @Test
    void failsOnlyWhenBothSourcesFail() {
        assertThatThrownBy(() -> new CompositePlazoFijoSource(officialDown, banksDown).fetch())
                .isInstanceOf(SourceException.class)
                .hasMessageContaining(OFFICIAL.id())
                .hasMessageContaining(BANKS.id());
    }
}
