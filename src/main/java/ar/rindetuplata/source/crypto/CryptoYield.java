package ar.rindetuplata.source.crypto;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Flagged;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.ValidationFlag;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Yield a platform pays on one currency. {@code apy} is a fraction (basis: APY, compounded). */
public record CryptoYield(
        SourceRef source,
        String platform,
        String currency,
        BigDecimal apy,
        LocalDate date,
        Instant fetchedAt,
        Confidence confidence,
        List<ValidationFlag> flags) implements Flagged {

    public CryptoYield {
        flags = List.copyOf(flags);
    }
}
