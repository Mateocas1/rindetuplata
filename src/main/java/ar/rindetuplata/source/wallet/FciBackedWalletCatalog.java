package ar.rindetuplata.source.wallet;

import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.JsonSupport;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.SourceException;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.ValidationFlag;
import ar.rindetuplata.source.fund.FundQuote;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;

/**
 * Which wallets are backed by which fund class (S12), from the classpath mapping {@code fci-backed-wallets.json}.
 * A class is mapped only once it is confirmed in the fund response.
 */
public class FciBackedWalletCatalog {

    static final SourceRef SOURCE = new SourceRef("fci-backed-wallets", "FCI-backed wallet mapping", false);
    private static final String RESOURCE = "fci-backed-wallets.json";

    private final List<FciBackedWalletMapping> mappings;

    private FciBackedWalletCatalog(List<FciBackedWalletMapping> mappings) {
        this.mappings = List.copyOf(mappings);
    }

    public static FciBackedWalletCatalog fromClasspath() {
        try {
            return fromJson(new ClassPathResource(RESOURCE).getContentAsByteArray());
        } catch (IOException e) {
            throw new SourceException(SOURCE, "cannot read " + RESOURCE, e);
        }
    }

    public static FciBackedWalletCatalog fromJson(byte[] json) {
        JsonNode wallets = JsonSupport.array(SOURCE, JsonSupport.readTree(SOURCE, json), "wallets");
        List<FciBackedWalletMapping> mappings = new ArrayList<>();
        for (JsonNode wallet : wallets) {
            String id = JsonSupport.text(wallet, "id");
            String name = JsonSupport.text(wallet, "name");
            String fund = JsonSupport.text(wallet, "fund");
            if (id == null || name == null || fund == null) {
                throw new SourceException(SOURCE, "each wallet needs id, name and fund", null);
            }
            mappings.add(new FciBackedWalletMapping(id, name, fund));
        }
        return new FciBackedWalletCatalog(mappings);
    }

    public List<FciBackedWalletMapping> mappings() {
        return mappings;
    }

    /** Pairs each mapping with the quote of its class; an absent class is kept as INVALID, never dropped. */
    public List<FciBackedWallet> resolve(List<FundQuote> quotes) {
        List<FciBackedWallet> wallets = new ArrayList<>();
        for (FciBackedWalletMapping mapping : mappings) {
            FundQuote quote = quotes.stream().filter(q -> mapping.fundClass().equals(q.name())).findFirst().orElse(null);
            List<ValidationFlag> flags = quote != null ? quote.flags()
                    : List.of(new ValidationFlag(FlagCode.FUND_CLASS_NOT_FOUND, Severity.INVALID,
                            "Fund class not found: " + mapping.fundClass()));
            wallets.add(new FciBackedWallet(mapping.walletId(), mapping.name(), mapping.fundClass(), quote, flags));
        }
        return wallets;
    }
}
