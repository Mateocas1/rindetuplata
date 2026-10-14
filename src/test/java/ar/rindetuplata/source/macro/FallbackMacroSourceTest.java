package ar.rindetuplata.source.macro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.SourceException;
import ar.rindetuplata.source.SourceRef;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FallbackMacroSourceTest {

    private static final SourceRef PRIMARY = BcraMonetariasClient.SOURCE;
    private static final SourceRef SECONDARY = ArgentinaDatosInflationAdapter.SOURCE;

    private static MacroObservation observation(SourceRef source) {
        return new MacroObservation(source, MacroVariable.MONTHLY_INFLATION, LocalDate.parse("2026-09-30"),
                new BigDecimal("0.019"), Instant.parse("2026-10-14T12:00:00Z"), Confidence.OFFICIAL_AUTOMATIC, List.of());
    }

    private static MacroSource source(SourceRef ref, boolean fails, boolean supportsAll) {
        return new MacroSource() {
            @Override
            public boolean supports(MacroVariable variable) {
                return supportsAll || variable == MacroVariable.MONTHLY_INFLATION;
            }

            @Override
            public List<MacroObservation> fetch(MacroVariable variable) {
                if (fails) {
                    throw new SourceException(ref, "down", null);
                }
                return List.of(observation(ref));
            }
        };
    }

    @Test
    void usesThePrimaryWhenItWorks() {
        var fallback = new FallbackMacroSource(source(PRIMARY, false, true), source(SECONDARY, false, false));

        assertThat(fallback.fetch(MacroVariable.MONTHLY_INFLATION)).extracting(MacroObservation::source)
                .containsExactly(PRIMARY);
    }

    @Test
    void fallsBackToTheSecondaryWhenThePrimaryFails() {
        var fallback = new FallbackMacroSource(source(PRIMARY, true, true), source(SECONDARY, false, false));

        assertThat(fallback.fetch(MacroVariable.MONTHLY_INFLATION)).extracting(MacroObservation::source)
                .containsExactly(SECONDARY);
    }

    @Test
    void rethrowsThePrimaryFailureWhenTheSecondaryDoesNotSupportTheVariable() {
        var fallback = new FallbackMacroSource(source(PRIMARY, true, true), source(SECONDARY, false, false));

        assertThatThrownBy(() -> fallback.fetch(MacroVariable.EXPECTED_INFLATION_12M))
                .isInstanceOfSatisfying(SourceException.class, e -> assertThat(e.source()).isEqualTo(PRIMARY));
    }

    @Test
    void failsWhenBothFailKeepingTheSecondaryAsSuppressed() {
        var fallback = new FallbackMacroSource(source(PRIMARY, true, true), source(SECONDARY, true, false));

        assertThatThrownBy(() -> fallback.fetch(MacroVariable.MONTHLY_INFLATION))
                .isInstanceOfSatisfying(SourceException.class, e -> {
                    assertThat(e.source()).isEqualTo(PRIMARY);
                    assertThat(e.getSuppressed()).hasSize(1);
                });
    }
}
