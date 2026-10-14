package ar.rindetuplata.source.plazofijo;

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
 * One row of the BCRA transparency regime. {@code tea} is the official minimum TEA as a fraction (basis: TEA).
 * {@code termCode} is the raw {@code plazoMinimoInvertirDias} value, a 0-5 code and not days.
 */
public record OfficialPlazoFijoRate(
        SourceRef source,
        Integer entityCode,
        String entityName,
        String productName,
        Segment segment,
        Integer termCode,
        BigDecimal minimumAmount,
        String channel,
        BigDecimal tea,
        LocalDate dataDate,
        Instant fetchedAt,
        Confidence confidence,
        List<ValidationFlag> flags) implements Flagged {

    public OfficialPlazoFijoRate {
        flags = List.copyOf(flags);
    }
}
