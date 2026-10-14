package ar.rindetuplata.source.wallet;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Flagged;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.ValidationFlag;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Rate of a remunerated wallet or account (basis: TNA, fractions). {@code tiers} is the unconditional schedule;
 * {@code conditions} are the "up to" alternatives. {@code verifiedAt} is set only for curated rates.
 */
public record WalletRate(
        SourceRef source,
        String walletId,
        String name,
        List<WalletTier> tiers,
        List<WalletCondition> conditions,
        LocalDate dataDate,
        LocalDate verifiedAt,
        Instant fetchedAt,
        Confidence confidence,
        List<ValidationFlag> flags) implements Flagged {

    public WalletRate {
        tiers = List.copyOf(tiers);
        conditions = List.copyOf(conditions);
        flags = List.copyOf(flags);
    }
}
