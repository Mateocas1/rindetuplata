package ar.rindetuplata.source.plazofijo;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Flagged;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.ValidationFlag;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A bank's plazo fijo rates as aggregated by ArgentinaDatos (basis: TNA, fractions). {@code nonClientTna} is
 * {@code null} when the bank does not offer the product to non-clients (the source reports {@code 0}).
 * The source has no date of its own: {@code dataDate} is the fetch date of the snapshot.
 */
public record BankPlazoFijoRate(
        SourceRef source,
        String bankName,
        BigDecimal clientTna,
        BigDecimal nonClientTna,
        List<TermRate> terms,
        LocalDate dataDate,
        Instant fetchedAt,
        Confidence confidence,
        List<ValidationFlag> flags) implements Flagged {

    public BankPlazoFijoRate {
        terms = List.copyOf(terms);
        flags = List.copyOf(flags);
    }
}
