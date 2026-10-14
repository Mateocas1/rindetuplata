package ar.rindetuplata.source.wallet;

import ar.rindetuplata.source.Flagged;
import ar.rindetuplata.source.fund.FundQuote;
import ar.rindetuplata.source.ValidationFlag;
import java.util.List;

/**
 * An FCI-backed wallet with the latest quote of its fund class; the yield itself is computed in T4 from
 * cuotaparte deltas. {@code quote} is {@code null} when the class is absent from the response.
 */
public record FciBackedWallet(
        String walletId,
        String name,
        String fundClass,
        FundQuote quote,
        List<ValidationFlag> flags) implements Flagged {

    public FciBackedWallet {
        flags = List.copyOf(flags);
    }
}
