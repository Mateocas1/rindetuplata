package ar.rindetuplata.source.fund;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Flagged;
import ar.rindetuplata.source.Segment;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.ValidationFlag;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Daily cuotaparte quote of a money-market fund class. {@code name} is the full class name as the source gives it;
 * {@code fund} and {@code shareClass} are its parts around the first {@code " - "} ({@code shareClass} may be
 * {@code null}). No yield math here: T4 derives it from {@code vcp} deltas.
 */
public record FundQuote(
        SourceRef source,
        String name,
        String fund,
        String shareClass,
        String horizon,
        Segment segment,
        LocalDate date,
        BigDecimal vcp,
        BigDecimal ccp,
        BigDecimal patrimonio,
        Instant fetchedAt,
        Confidence confidence,
        List<ValidationFlag> flags) implements Flagged {

    public FundQuote {
        flags = List.copyOf(flags);
    }
}
