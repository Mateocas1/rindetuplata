package ar.rindetuplata.source.macro;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Flagged;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.ValidationFlag;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** One dated value of a macro series, as a fraction (1.9% monthly inflation is {@code 0.019}). May be negative. */
public record MacroObservation(
        SourceRef source,
        MacroVariable variable,
        LocalDate date,
        BigDecimal value,
        Instant fetchedAt,
        Confidence confidence,
        List<ValidationFlag> flags) implements Flagged {

    public MacroObservation {
        flags = List.copyOf(flags);
    }
}
