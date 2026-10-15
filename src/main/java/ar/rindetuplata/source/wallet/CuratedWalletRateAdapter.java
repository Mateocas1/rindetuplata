package ar.rindetuplata.source.wallet;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.JsonSupport;
import ar.rindetuplata.source.SourceException;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.Units;
import ar.rindetuplata.source.ValidationFlag;
import ar.rindetuplata.source.ValidationRules;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

/**
 * Wallet rates curated by hand (S12). T6b moved the source of truth from the T3 classpath seed to the
 * {@code curated_wallet_rate} table of V4, which the admin panel writes; the adapter reads it and produces the same
 * {@link WalletRate} output, with the same {@code CURATED_MANUAL} confidence and the same 7-day STALE rule.
 *
 * <p>The {@code tiers} and {@code conditions} columns keep the document shape of the retired seed (TNA in percent,
 * floor and ceiling in ARS), so {@link #parse} is still the parser of that shape: the migration seeds it and the
 * admin API accepts it.
 */
public class CuratedWalletRateAdapter implements WalletRateSource {

    public static final SourceRef SOURCE = new SourceRef("curated-wallet-rates", "Curated by the project owner", false);

    private static final String SELECT = """
            select wallet_id, name, tiers, conditions, verified_at from curated_wallet_rate order by wallet_id
            """;

    private final JdbcClient jdbc;
    private final Clock clock;

    public CuratedWalletRateAdapter(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** The curated rows of V4 as wallet rates. A wallet verified more than 7 days ago is flagged STALE. */
    @Override
    public List<WalletRate> fetch() {
        Instant fetchedAt = clock.instant();
        LocalDate today = ValidationRules.localDate(fetchedAt);
        return jdbc.sql(SELECT)
                .query((rs, rowNum) -> rate(rs.getString("wallet_id"), rs.getString("name"),
                        document(rs.getString("tiers")), document(rs.getString("conditions")),
                        rs.getObject("verified_at", LocalDate.class), fetchedAt, today))
                .list();
    }

    /** Pure: a curated-wallet document → wallet rates. A wallet verified more than 7 days before the fetch is STALE. */
    public static List<WalletRate> parse(byte[] json, Instant fetchedAt) {
        JsonNode wallets = JsonSupport.array(SOURCE, JsonSupport.readTree(SOURCE, json), "wallets");
        LocalDate today = ValidationRules.localDate(fetchedAt);
        List<WalletRate> rates = new ArrayList<>();
        for (JsonNode wallet : wallets) {
            LocalDate verifiedAt = JsonSupport.date(SOURCE, wallet, "verifiedAt");
            if (verifiedAt == null) {
                throw new SourceException(SOURCE, "wallet '" + JsonSupport.text(wallet, "id") + "' has no verifiedAt", null);
            }
            rates.add(rate(JsonSupport.text(wallet, "id"), JsonSupport.text(wallet, "name"),
                    JsonSupport.array(SOURCE, wallet, "tiers"), wallet.path("conditions"), verifiedAt, fetchedAt, today));
        }
        return rates;
    }

    /**
     * One curated row as a wallet rate, the same mapping {@link #fetch()} applies. The curation panel uses it to
     * publish today's snapshot of a rate it just saved; verification older than 7 days is flagged STALE.
     */
    public static WalletRate toWalletRate(String walletId, String name, JsonNode tiers, JsonNode conditions,
            LocalDate verifiedAt, Instant fetchedAt) {
        return rate(walletId, name, tiers, conditions, verifiedAt, fetchedAt, ValidationRules.localDate(fetchedAt));
    }

    private static WalletRate rate(String walletId, String name, JsonNode tiersNode, JsonNode conditionsNode,
            LocalDate verifiedAt, Instant fetchedAt, LocalDate today) {
        List<ValidationFlag> flags = new ArrayList<>();
        List<WalletTier> tiers = tiers(tiersNode, flags);
        List<WalletCondition> conditions = conditions(conditionsNode, flags);
        flags.addAll(ValidationRules.stalenessFlags(verifiedAt, today, ValidationRules.CURATED_WALLET_STALE_DAYS));
        return new WalletRate(SOURCE, walletId, name, tiers, conditions, verifiedAt, verifiedAt, fetchedAt,
                Confidence.CURATED_MANUAL, flags);
    }

    /** The {@code conditions} column is optional and may be absent, so a missing or non-array value means "none". */
    private static List<WalletCondition> conditions(JsonNode nodes, List<ValidationFlag> flags) {
        List<WalletCondition> conditions = new ArrayList<>();
        if (nodes == null || !nodes.isArray()) {
            return conditions;
        }
        for (JsonNode node : nodes) {
            conditions.add(new WalletCondition(JsonSupport.text(node, "condition"),
                    tiers(JsonSupport.array(SOURCE, node, "tiers"), flags)));
        }
        return conditions;
    }

    private static List<WalletTier> tiers(JsonNode nodes, List<ValidationFlag> flags) {
        List<WalletTier> tiers = new ArrayList<>();
        if (nodes == null || !nodes.isArray()) {
            return tiers;
        }
        for (JsonNode node : nodes) {
            BigDecimal tna = Units.percentToFraction(JsonSupport.decimal(SOURCE, node, "tnaPercent"));
            flags.addAll(ValidationRules.rateFlags(tna));
            tiers.add(new WalletTier(JsonSupport.decimal(SOURCE, node, "floor"),
                    JsonSupport.decimal(SOURCE, node, "ceiling"), tna));
        }
        return tiers;
    }

    /** A {@code jsonb} column as JSON. Malformed stored JSON is a source failure, never a silent empty list. */
    private static JsonNode document(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JsonSupport.readTree(SOURCE, json.getBytes(StandardCharsets.UTF_8));
    }
}
