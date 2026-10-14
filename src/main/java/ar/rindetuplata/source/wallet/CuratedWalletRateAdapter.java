package ar.rindetuplata.source.wallet;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.JsonSupport;
import ar.rindetuplata.source.SourceException;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.Units;
import ar.rindetuplata.source.ValidationFlag;
import ar.rindetuplata.source.ValidationRules;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.Resource;
import tools.jackson.databind.JsonNode;

/** Wallet rates loaded by hand from a classpath seed (S12). T6 replaces the seed with the admin panel. */
public class CuratedWalletRateAdapter implements WalletRateSource {

    public static final SourceRef SOURCE = new SourceRef("curated-wallet-rates", "Curated by the project owner", false);

    private final Resource seed;
    private final Clock clock;

    public CuratedWalletRateAdapter(Resource seed, Clock clock) {
        this.seed = seed;
        this.clock = clock;
    }

    @Override
    public List<WalletRate> fetch() {
        Instant fetchedAt = clock.instant();
        try {
            return parse(seed.getContentAsByteArray(), fetchedAt);
        } catch (IOException e) {
            throw new SourceException(SOURCE, "cannot read the curated seed " + seed, e);
        }
    }

    /** Pure: seed JSON → wallet rates. A wallet verified more than 7 days before {@code fetchedAt} is flagged STALE. */
    public static List<WalletRate> parse(byte[] json, Instant fetchedAt) {
        JsonNode wallets = JsonSupport.array(SOURCE, JsonSupport.readTree(SOURCE, json), "wallets");
        LocalDate today = ValidationRules.localDate(fetchedAt);
        List<WalletRate> rates = new ArrayList<>();
        for (JsonNode wallet : wallets) {
            LocalDate verifiedAt = JsonSupport.date(SOURCE, wallet, "verifiedAt");
            if (verifiedAt == null) {
                throw new SourceException(SOURCE, "wallet '" + JsonSupport.text(wallet, "id") + "' has no verifiedAt", null);
            }
            List<ValidationFlag> flags = new ArrayList<>();
            List<WalletTier> tiers = tiers(JsonSupport.array(SOURCE, wallet, "tiers"), flags);
            List<WalletCondition> conditions = new ArrayList<>();
            JsonNode conditionNodes = wallet.path("conditions");
            if (conditionNodes.isArray()) {
                for (JsonNode condition : conditionNodes) {
                    conditions.add(new WalletCondition(JsonSupport.text(condition, "condition"),
                            tiers(JsonSupport.array(SOURCE, condition, "tiers"), flags)));
                }
            }
            flags.addAll(ValidationRules.stalenessFlags(verifiedAt, today, ValidationRules.CURATED_WALLET_STALE_DAYS));
            rates.add(new WalletRate(SOURCE, JsonSupport.text(wallet, "id"), JsonSupport.text(wallet, "name"), tiers,
                    conditions, verifiedAt, verifiedAt, fetchedAt, Confidence.CURATED_MANUAL, flags));
        }
        return rates;
    }

    private static List<WalletTier> tiers(JsonNode nodes, List<ValidationFlag> flags) {
        List<WalletTier> tiers = new ArrayList<>();
        for (JsonNode node : nodes) {
            BigDecimal tna = Units.percentToFraction(JsonSupport.decimal(SOURCE, node, "tnaPercent"));
            flags.addAll(ValidationRules.rateFlags(tna));
            tiers.add(new WalletTier(JsonSupport.decimal(SOURCE, node, "floor"),
                    JsonSupport.decimal(SOURCE, node, "ceiling"), tna));
        }
        return tiers;
    }
}
