package ar.rindetuplata.source.dollar;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Flagged;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.ValidationFlag;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Dólar MEP ("bolsa") in ARS per USD. {@code updatedAt} is the instant the source reports. */
public record DollarQuote(
        SourceRef source,
        BigDecimal buy,
        BigDecimal sell,
        Instant updatedAt,
        Instant fetchedAt,
        Confidence confidence,
        List<ValidationFlag> flags) implements Flagged {

    public DollarQuote {
        flags = List.copyOf(flags);
    }
}
