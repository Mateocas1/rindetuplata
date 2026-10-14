package ar.rindetuplata.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ValidationRulesTest {

    @Test
    void nullRateIsInvalid() {
        assertThat(ValidationRules.rateFlags(null)).extracting(ValidationFlag::code, ValidationFlag::severity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(FlagCode.NON_POSITIVE_RATE, Severity.INVALID));
    }

    @Test
    void zeroAndNegativeRatesAreInvalid() {
        assertThat(ValidationRules.rateFlags(BigDecimal.ZERO)).extracting(ValidationFlag::code)
                .containsExactly(FlagCode.NON_POSITIVE_RATE);
        assertThat(ValidationRules.rateFlags(new BigDecimal("-0.01"))).extracting(ValidationFlag::code)
                .containsExactly(FlagCode.NON_POSITIVE_RATE);
    }

    @Test
    void rateAboveThreeHundredPercentIsInvalidButExactlyThreeHundredIsNot() {
        assertThat(ValidationRules.rateFlags(new BigDecimal("3.0"))).isEmpty();
        assertThat(ValidationRules.rateFlags(new BigDecimal("3.01"))).extracting(ValidationFlag::code, ValidationFlag::severity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(FlagCode.RATE_ABOVE_MAXIMUM, Severity.INVALID));
    }

    @Test
    void plausibleRateHasNoFlags() {
        assertThat(ValidationRules.rateFlags(new BigDecimal("0.2075"))).isEmpty();
    }

    @Test
    void staleWhenOlderThanTheWindow() {
        LocalDate today = LocalDate.parse("2026-10-14");
        assertThat(ValidationRules.stalenessFlags(LocalDate.parse("2026-08-30"), today, 45)).isEmpty();
        assertThat(ValidationRules.stalenessFlags(LocalDate.parse("2026-08-29"), today, 45))
                .extracting(ValidationFlag::code, ValidationFlag::severity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(FlagCode.STALE, Severity.WARNING));
    }

    @Test
    void missingDateIsNotStale() {
        assertThat(ValidationRules.stalenessFlags(null, LocalDate.parse("2026-10-14"), 7)).isEmpty();
    }

    @Test
    void invalidDetectionLooksAtSeverity() {
        var warning = new ValidationFlag(FlagCode.STALE, Severity.WARNING, "old");
        var invalid = new ValidationFlag(FlagCode.NON_POSITIVE_RATE, Severity.INVALID, "zero");
        assertThat(ValidationRules.hasInvalid(java.util.List.of(warning))).isFalse();
        assertThat(ValidationRules.hasInvalid(java.util.List.of(warning, invalid))).isTrue();
    }

    @Test
    void classifiesSegmentsFromProductNames() {
        assertThat(ValidationRules.segmentOf("PF PPJJ")).isEqualTo(Segment.LEGAL_ENTITY);
        assertThat(ValidationRules.segmentOf("PF PRECANCELABLE PPJJ")).isEqualTo(Segment.LEGAL_ENTITY);
        assertThat(ValidationRules.segmentOf("PLAZO FIJO EN DOLARES")).isEqualTo(Segment.FOREIGN_CURRENCY);
        assertThat(ValidationRules.segmentOf("PF TRADIC. DOLAR SUCURSAL")).isEqualTo(Segment.FOREIGN_CURRENCY);
        assertThat(ValidationRules.segmentOf("Fima Premium Dólares - Clase A")).isEqualTo(Segment.FOREIGN_CURRENCY);
        assertThat(ValidationRules.segmentOf("PFPRECANCELABLEUVA")).isEqualTo(Segment.UVA_ADJUSTED);
        assertThat(ValidationRules.segmentOf("PLAZO FIJO UVA")).isEqualTo(Segment.UVA_ADJUSTED);
        assertThat(ValidationRules.segmentOf("PFTRADICIONAL")).isEqualTo(Segment.SAVER);
        assertThat(ValidationRules.segmentOf("PF TRADICIONAL")).isEqualTo(Segment.SAVER);
        assertThat(ValidationRules.segmentOf(null)).isEqualTo(Segment.SAVER);
    }
}
